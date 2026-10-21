package com.casajojoapp.backend.service.impl;

import com.casajojoapp.backend.dto.ItemMenuImportado;
import com.casajojoapp.backend.dto.response.ItemMenuResponse;
import com.casajojoapp.backend.dto.response.MenuDiarioResponse;
import com.casajojoapp.backend.entity.ItemMenu;
import com.casajojoapp.backend.entity.MenuDiario;
import com.casajojoapp.backend.enums.EstadoMenu;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.exception.ResourceNotFoundException;
import com.casajojoapp.backend.exception.SincronizacionException;
import com.casajojoapp.backend.parser.MenuCsvParser;
import com.casajojoapp.backend.repository.ItemMenuRepository;
import com.casajojoapp.backend.repository.MenuDiarioRepository;
import com.casajojoapp.backend.service.MenuService;
import com.casajojoapp.backend.source.MenuCsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);

    private final ItemMenuRepository itemMenuRepository;
    private final MenuDiarioRepository menuDiarioRepository;
    private final MenuCsvSource csvSource;
    private final MenuCsvParser parser;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int stockPorDefecto;

    public MenuServiceImpl(ItemMenuRepository itemMenuRepository,
                           MenuDiarioRepository menuDiarioRepository,
                           MenuCsvSource csvSource,
                           MenuCsvParser parser,
                           PlatformTransactionManager transactionManager,
                           Clock clock,
                           @Value("${menu.stock-por-defecto:50}") int stockPorDefecto) {
        this.itemMenuRepository = itemMenuRepository;
        this.menuDiarioRepository = menuDiarioRepository;
        this.csvSource = csvSource;
        this.parser = parser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.stockPorDefecto = stockPorDefecto;
    }

    /**
     * La descarga del Sheet se hace fuera de la transaccion: solo se abre una transaccion
     * cuando ya hay datos validos que guardar.
     */
    @Override
    public MenuDiarioResponse sincronizarConGoogleSheets() {
        MenuCsvParser.Resultado resultado = parser.parsear(csvSource.descargar());

        if (resultado.filasIgnoradas() > 0) {
            log.warn("Sincronización: {} filas del Sheet fueron ignoradas por estar incompletas o duplicadas.",
                    resultado.filasIgnoradas());
        }

        MenuDiarioResponse respuesta = transactionTemplate.execute(status -> aplicarUpsert(resultado.items()));
        if (respuesta == null) {
            throw new SincronizacionException("No se pudo guardar el menú sincronizado.");
        }
        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public MenuDiarioResponse obtenerMenuVigente() {
        MenuDiario menu = menuDeHoy();
        return aResponse(menu, itemMenuRepository.findAll(Sort.by("id")));
    }

    @Override
    @Transactional
    public MenuDiarioResponse cambiarEstadoMenu(EstadoMenu estado) {
        MenuDiario menu = menuDeHoy();
        menu.setEstado(estado);
        menuDiarioRepository.save(menu);
        return aResponse(menu, itemMenuRepository.findAll(Sort.by("id")));
    }

    @Override
    @Transactional
    public ItemMenu descontarStock(Long itemId, int cantidad) {
        ItemMenu item = itemMenuRepository.findWithLockById(itemId)
                .orElseThrow(() -> BusinessRuleException.invalida(
                        "El ítem con id " + itemId + " no existe en el menú vigente."));

        if (!Boolean.TRUE.equals(item.getDisponible())) {
            throw BusinessRuleException.reglaNegocio("El ítem \"" + item.getNombre() + "\" no está disponible hoy.");
        }
        if (item.getStockActual() < cantidad) {
            throw BusinessRuleException.conflicto("Stock insuficiente de \"" + item.getNombre()
                    + "\": quedan " + item.getStockActual() + " y se pidieron " + cantidad + ".");
        }

        item.setStockActual(item.getStockActual() - cantidad);
        return itemMenuRepository.save(item);
    }

    private MenuDiario menuDeHoy() {
        return menuDiarioRepository.findByFecha(LocalDate.now(clock))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aún no hay un menú sincronizado para hoy. Ejecuta la sincronización."));
    }

    private MenuDiarioResponse aplicarUpsert(List<ItemMenuImportado> importados) {
        LocalDate hoy = LocalDate.now(clock);
        MenuDiario menu = menuDiarioRepository.findByFecha(hoy).orElse(null);
        boolean nuevoDia = menu == null;
        if (nuevoDia) {
            menu = new MenuDiario();
            menu.setFecha(hoy);
            menu.setEstado(EstadoMenu.ABIERTO);
        }
        menu.setUltimaSincronizacion(LocalDateTime.now(clock));
        menu = menuDiarioRepository.save(menu);

        List<ItemMenu> existentes = itemMenuRepository.findAll();

        Map<Long, ItemMenu> porCodigo = new HashMap<>();
        Map<String, ItemMenu> porNombre = new HashMap<>();
        for (ItemMenu item : existentes) {
            if (item.getCodigoExterno() != null) {
                porCodigo.put(item.getCodigoExterno(), item);
            }
            porNombre.putIfAbsent(claveNombre(item.getCategoria(), item.getNombre()), item);
        }

        List<ItemMenu> actualizados = new ArrayList<>();
        Set<Long> idsConservados = new HashSet<>();

        for (ItemMenuImportado importado : importados) {
            ItemMenu item = importado.codigoExterno() != null
                    ? porCodigo.get(importado.codigoExterno())
                    : porNombre.get(claveNombre(importado.categoria(), importado.nombre()));
            boolean esNuevo = item == null;
            if (esNuevo) {
                item = new ItemMenu();
            }

            // Stock: un dia nuevo parte de cero ventas. Una re-sincronizacion el mismo dia
            // respeta lo ya vendido (nunca "devuelve" stock que ya se descontó).
            int stockInicial = importado.stock() != null ? importado.stock() : stockPorDefecto;
            int stockActual;
            if (esNuevo || nuevoDia) {
                stockActual = stockInicial;
            } else {
                int vendido = Math.max(0, item.getStockInicial() - item.getStockActual());
                stockActual = Math.max(0, stockInicial - vendido);
            }

            item.setCodigoExterno(importado.codigoExterno());
            item.setCategoria(importado.categoria());
            item.setNombre(importado.nombre());
            item.setDescripcion(importado.descripcion());
            item.setPrecio(BigDecimal.valueOf(importado.precio()).setScale(2, RoundingMode.HALF_UP));
            item.setStockInicial(stockInicial);
            item.setStockActual(stockActual);
            item.setDisponible(importado.disponible());

            if (item.getId() != null) {
                idsConservados.add(item.getId());
            }
            actualizados.add(item);
        }

        List<Long> idsAEliminar = existentes.stream()
                .map(ItemMenu::getId)
                .filter(id -> !idsConservados.contains(id))
                .toList();
        if (!idsAEliminar.isEmpty()) {
            itemMenuRepository.deleteAllByIdInBatch(idsAEliminar);
            log.info("Sincronización: {} ítems eliminados por no estar en el Sheet.", idsAEliminar.size());
        }

        List<ItemMenu> guardados = itemMenuRepository.saveAll(actualizados);
        return aResponse(menu, guardados);
    }

    private String claveNombre(String categoria, String nombre) {
        return MenuCsvParser.normalizar(categoria) + "|" + MenuCsvParser.normalizar(nombre);
    }

    private MenuDiarioResponse aResponse(MenuDiario menu, List<ItemMenu> items) {
        List<ItemMenuResponse> respuesta = items.stream()
                .sorted(Comparator.comparing(ItemMenu::getId))
                .map(this::aItemResponse)
                .toList();
        return new MenuDiarioResponse(menu.getFecha().toString(), menu.getEstado().name(), respuesta);
    }

    private ItemMenuResponse aItemResponse(ItemMenu i) {
        boolean disponible = Boolean.TRUE.equals(i.getDisponible()) && i.getStockActual() > 0;
        return new ItemMenuResponse(i.getId(), i.getCategoria(), i.getNombre(), i.getDescripcion(),
                i.getPrecio(), i.getStockActual(), disponible);
    }
}
