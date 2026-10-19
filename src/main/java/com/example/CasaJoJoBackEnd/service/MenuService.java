package com.example.CasaJoJoBackEnd.service;

import com.example.CasaJoJoBackEnd.dto.ProductoDTO;
import com.example.CasaJoJoBackEnd.dto.ProductoImportado;
import com.example.CasaJoJoBackEnd.model.Producto;
import com.example.CasaJoJoBackEnd.parser.MenuCsvParser;
import com.example.CasaJoJoBackEnd.repository.IProductoRepository;
import com.example.CasaJoJoBackEnd.source.MenuCsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    private final IProductoRepository productoRepository;
    private final MenuCsvSource csvSource;
    private final MenuCsvParser parser;
    private final TransactionTemplate transactionTemplate;

    public MenuService(IProductoRepository productoRepository,
                       MenuCsvSource csvSource,
                       MenuCsvParser parser,
                       PlatformTransactionManager transactionManager) {
        this.productoRepository = productoRepository;
        this.csvSource = csvSource;
        this.parser = parser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<ProductoDTO> sincronizarDesdeSheets() {
        MenuCsvParser.Resultado resultado = parser.parsear(csvSource.descargar());

        if (resultado.filasIgnoradas() > 0) {
            log.warn("Sincronización: {} filas del Sheet fueron ignoradas por estar incompletas o duplicadas.",
                    resultado.filasIgnoradas());
        }

        List<Producto> guardados = transactionTemplate.execute(status -> aplicarUpsert(resultado.productos()));
        if (guardados == null) {
            guardados = List.of();
        }

        return guardados.stream().map(this::aDto).toList();
    }

    public List<ProductoDTO> obtenerProductos() {
        return productoRepository.findAll().stream().map(this::aDto).toList();
    }

    private List<Producto> aplicarUpsert(List<ProductoImportado> importados) {
        List<Producto> existentes = productoRepository.findAll();

        Map<Long, Producto> porCodigo = new HashMap<>();
        Map<String, Producto> porNombre = new HashMap<>();
        for (Producto producto : existentes) {
            if (producto.getCodigoExterno() != null) {
                porCodigo.put(producto.getCodigoExterno(), producto);
            }
            porNombre.putIfAbsent(claveNombre(producto.getCategoria(), producto.getNombre()), producto);
        }

        List<Producto> actualizados = new ArrayList<>();
        Set<Long> idsConservados = new HashSet<>();

        for (ProductoImportado importado : importados) {
            Producto producto = importado.codigoExterno() != null
                    ? porCodigo.get(importado.codigoExterno())
                    : porNombre.get(claveNombre(importado.categoria(), importado.nombre()));
            if (producto == null) {
                producto = new Producto();
            }

            producto.setCodigoExterno(importado.codigoExterno());
            producto.setCategoria(importado.categoria());
            producto.setNombre(importado.nombre());
            producto.setDescripcion(importado.descripcion());
            producto.setPrecioExtra(importado.precioExtra());
            producto.setDisponible(importado.disponible());

            if (producto.getId() != null) {
                idsConservados.add(producto.getId());
            }
            actualizados.add(producto);
        }

        List<Long> idsAEliminar = existentes.stream()
                .map(Producto::getId)
                .filter(id -> !idsConservados.contains(id))
                .toList();
        if (!idsAEliminar.isEmpty()) {
            productoRepository.deleteAllByIdInBatch(idsAEliminar);
            log.info("Sincronización: {} productos eliminados por no estar en el Sheet.", idsAEliminar.size());
        }

        return productoRepository.saveAll(actualizados);
    }

    private String claveNombre(String categoria, String nombre) {
        return MenuCsvParser.normalizar(categoria) + "|" + MenuCsvParser.normalizar(nombre);
    }

    private ProductoDTO aDto(Producto p) {
        return new ProductoDTO(p.getId(), p.getCategoria(), p.getNombre(), p.getDescripcion(),
                p.getPrecioExtra(), p.getDisponible());
    }
}
