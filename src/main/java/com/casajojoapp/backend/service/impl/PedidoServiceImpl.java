package com.casajojoapp.backend.service.impl;

import com.casajojoapp.backend.dto.request.CrearPedidoRequest;
import com.casajojoapp.backend.dto.request.ItemPedidoRequest;
import com.casajojoapp.backend.dto.response.DetallePedidoResponse;
import com.casajojoapp.backend.dto.response.ItemCocinaResponse;
import com.casajojoapp.backend.dto.response.PedidoResponse;
import com.casajojoapp.backend.dto.response.ResumenCocinaResponse;
import com.casajojoapp.backend.entity.DetallePedido;
import com.casajojoapp.backend.entity.ItemMenu;
import com.casajojoapp.backend.entity.MenuDiario;
import com.casajojoapp.backend.entity.Pedido;
import com.casajojoapp.backend.enums.EstadoMenu;
import com.casajojoapp.backend.enums.EstadoPedido;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.exception.ResourceNotFoundException;
import com.casajojoapp.backend.repository.MenuDiarioRepository;
import com.casajojoapp.backend.repository.PedidoRepository;
import com.casajojoapp.backend.service.MenuService;
import com.casajojoapp.backend.service.PedidoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class PedidoServiceImpl implements PedidoService {

    private static final int MINUTOS_MINIMOS_ANTICIPACION = 20;
    private static final int MAX_OBSERVACIONES = 500;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<EstadoPedido> ESTADOS_ACTIVOS =
            List.of(EstadoPedido.RECIBIDO, EstadoPedido.EN_PREPARACION);

    private final PedidoRepository pedidoRepository;
    private final MenuDiarioRepository menuDiarioRepository;
    private final MenuService menuService;
    private final Clock clock;

    public PedidoServiceImpl(PedidoRepository pedidoRepository,
                             MenuDiarioRepository menuDiarioRepository,
                             MenuService menuService,
                             Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.menuDiarioRepository = menuDiarioRepository;
        this.menuService = menuService;
        this.clock = clock;
    }

    /**
     * Todo ocurre en una sola transaccion: si cualquier ítem no tiene stock, se deshacen
     * los descuentos de los demás y no se guarda nada.
     */
    @Override
    @Transactional
    public PedidoResponse crearPedido(CrearPedidoRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw BusinessRuleException.invalida("El pedido debe incluir al menos un ítem del menú.");
        }
        String nombre = request.nombreCliente() == null ? "" : request.nombreCliente().trim();
        if (nombre.isEmpty()) {
            throw BusinessRuleException.invalida("El nombre del cliente es obligatorio.");
        }

        // Se agrupan las lineas por (item, plato, observacion): el mismo
        // producto pedido en DOS platos distintos queda en dos lineas para
        // conservar el platoId (la appAndroid pinta "PLATO 1 DE 2", etc.).
        // El orden de PROCESAMIENTO sigue siendo por itemMenuId para
        // bloquear siempre en el mismo orden y evitar interbloqueos.
        record ClaveDetalle(Long itemMenuId, Integer platoId, String observacion) {}
        Map<ClaveDetalle, Integer> cantidades = new java.util.LinkedHashMap<>();
        for (ItemPedidoRequest item : request.items()) {
            if (item == null || item.itemMenuId() == null || item.cantidad() == null || item.cantidad() < 1) {
                throw BusinessRuleException.invalida("Cada ítem debe indicar itemMenuId y una cantidad mayor o igual a 1.");
            }
            String observacion = item.observacion() == null || item.observacion().trim().isBlank()
                    ? null : item.observacion().trim();
            cantidades.merge(
                    new ClaveDetalle(item.itemMenuId(), item.platoId(), observacion),
                    item.cantidad(),
                    Integer::sum);
        }
        List<Map.Entry<ClaveDetalle, Integer>> lineasAProcesar = cantidades.entrySet().stream()
                .sorted(java.util.Comparator
                        .comparing((Map.Entry<ClaveDetalle, Integer> e) -> e.getKey().itemMenuId())
                        .thenComparingInt(e -> java.util.Objects.requireNonNullElse(e.getKey().platoId(), 0)))
                .toList();

        LocalDateTime ahora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        MenuDiario menu = menuDiarioRepository.findByFecha(ahora.toLocalDate())
                .orElseThrow(() -> BusinessRuleException.reglaNegocio("No hay un menú publicado para hoy."));
        if (menu.getEstado() != EstadoMenu.ABIERTO) {
            throw BusinessRuleException.reglaNegocio("El menú de hoy está cerrado: ya no se reciben pedidos.");
        }

        LocalTime horaRetiro = parsearHora(request.horaRetiroSolicitada());
        if (ahora.toLocalDate().atTime(horaRetiro).isBefore(ahora.plusMinutes(MINUTOS_MINIMOS_ANTICIPACION))) {
            throw BusinessRuleException.reglaNegocio("La hora de retiro debe ser al menos "
                    + MINUTOS_MINIMOS_ANTICIPACION + " minutos posterior a la hora actual (" + ahora.format(HORA) + ").");
        }

        String observaciones = request.observaciones() == null ? null : request.observaciones().trim();
        if (observaciones != null && observaciones.isEmpty()) {
            observaciones = null;
        }
        if (observaciones != null && observaciones.length() > MAX_OBSERVACIONES) {
            throw BusinessRuleException.invalida(
                    "Las observaciones no pueden superar " + MAX_OBSERVACIONES + " caracteres.");
        }
        String telefono = request.telefonoContacto() == null ? null : request.telefonoContacto().trim();
        if (telefono != null && telefono.isEmpty()) {
            telefono = null;
        }

        Pedido pedido = new Pedido();
        pedido.setUsuarioId(request.usuarioId());
        pedido.setNombreCliente(nombre);
        pedido.setTelefonoContacto(telefono);
        pedido.setHoraRetiro(horaRetiro);
        pedido.setObservaciones(observaciones);
        pedido.setEstado(EstadoPedido.RECIBIDO);
        pedido.setFechaCreacion(clock.instant());

        // El precio lo calcula el servidor y queda congelado en cada detalle (precio_historico).
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<ClaveDetalle, Integer> entrada : lineasAProcesar) {
            ClaveDetalle clave = entrada.getKey();
            ItemMenu item = menuService.descontarStock(clave.itemMenuId(), entrada.getValue());

            DetallePedido detalle = new DetallePedido();
            detalle.setItemMenuId(item.getId());
            detalle.setNombreItem(item.getNombre());
            detalle.setCantidad(entrada.getValue());
            detalle.setPrecioHistorico(item.getPrecio());
            detalle.setObservacion(clave.observacion());
            detalle.setPlatoId(clave.platoId());
            pedido.agregarDetalle(detalle);

            total = total.add(item.getPrecio().multiply(BigDecimal.valueOf(entrada.getValue())));
        }
        pedido.setMontoTotal(total.setScale(2, RoundingMode.HALF_UP));
        pedido.setCodigoSeguimiento(generarCodigoSeguimiento());

        return aResponse(pedidoRepository.save(pedido));
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedido(Long id) {
        return aResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoResponse obtenerPorCodigoSeguimiento(String codigo) {
        String buscado = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        return aResponse(pedidoRepository.findByCodigoSeguimiento(buscado)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un pedido con el código de seguimiento " + buscado + ".")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos(String estado) {
        List<Pedido> pedidos = (estado == null || estado.isBlank())
                ? pedidoRepository.findAllByOrderByHoraRetiroAscIdAsc()
                : pedidoRepository.findByEstadoOrderByHoraRetiroAscIdAsc(EstadoPedido.desde(estado));
        return pedidos.stream().map(this::aResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponse> obtenerColaCocina() {
        return pedidoRepository.findByEstadoInOrderByHoraRetiroAscIdAsc(ESTADOS_ACTIVOS).stream()
                .map(this::aResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenCocinaResponse obtenerResumenCocina() {
        List<Pedido> activos = pedidoRepository.findByEstadoInOrderByHoraRetiroAscIdAsc(ESTADOS_ACTIVOS);

        int recibidos = 0;
        int enPreparacion = 0;
        Map<String, Integer> porItem = new TreeMap<>();
        for (Pedido pedido : activos) {
            if (pedido.getEstado() == EstadoPedido.RECIBIDO) {
                recibidos++;
            } else {
                enPreparacion++;
            }
            for (DetallePedido detalle : pedido.getDetalles()) {
                porItem.merge(detalle.getNombreItem(), detalle.getCantidad(), Integer::sum);
            }
        }

        List<ItemCocinaResponse> items = porItem.entrySet().stream()
                .map(e -> new ItemCocinaResponse(e.getKey(), e.getValue()))
                .toList();
        return new ResumenCocinaResponse(activos.size(), recibidos, enPreparacion, items);
    }

    @Override
    @Transactional
    public PedidoResponse actualizarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = buscar(id);
        EstadoPedido actual = pedido.getEstado();
        EstadoPedido permitido = actual.siguiente();

        if (permitido == null) {
            throw BusinessRuleException.conflicto("El pedido ya fue ENTREGADO y no admite más cambios de estado.");
        }
        if (nuevoEstado != permitido && nuevoEstado != EstadoPedido.CANCELADO) {
            throw BusinessRuleException.conflicto("Transición no permitida: " + actual + " a " + nuevoEstado
                    + ". El siguiente estado válido es " + permitido + ".");
        }
        if (nuevoEstado == EstadoPedido.CANCELADO && (actual == EstadoPedido.LISTO_PARA_RETIRO || actual == EstadoPedido.ENTREGADO)) {
            throw BusinessRuleException.conflicto("No se puede cancelar un pedido en estado " + actual + ".");
        }

        pedido.setEstado(nuevoEstado);
        return aResponse(pedidoRepository.save(pedido));
    }

    private Pedido buscar(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un pedido con id " + id + "."));
    }

    private LocalTime parsearHora(String valor) {
        if (valor == null || valor.isBlank()) {
            throw BusinessRuleException.invalida("La hora de retiro es obligatoria (formato HH:mm).");
        }
        try {
            return LocalTime.parse(valor.trim(), HORA);
        } catch (DateTimeParseException e) {
            throw BusinessRuleException.invalida("La hora de retiro debe tener formato HH:mm, por ejemplo 13:30.");
        }
    }

    /** Codigo corto tipo CJ-98F2. Si se agotan las combinaciones libres, crece un caracter. */
    private String generarCodigoSeguimiento() {
        int longitud = 4;
        for (int intento = 1; ; intento++) {
            String codigo = "CJ-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, longitud).toUpperCase(Locale.ROOT);
            if (!pedidoRepository.existsByCodigoSeguimiento(codigo)) {
                return codigo;
            }
            if (intento % 10 == 0 && longitud < 12) {
                longitud++;
            }
        }
    }

    private PedidoResponse aResponse(Pedido p) {
        List<DetallePedidoResponse> detalles = p.getDetalles().stream()
                .map(d -> new DetallePedidoResponse(d.getItemMenuId(), d.getNombreItem(), d.getCantidad(), d.getPrecioHistorico(), d.getObservacion(), d.getPlatoId()))
                .toList();
        return new PedidoResponse(
                p.getId(),
                p.getCodigoSeguimiento(),
                p.getEstado().name(),
                p.getHoraRetiro().format(HORA),
                p.getNombreCliente(),
                p.getTelefonoContacto(),
                p.getObservaciones(),
                p.getMontoTotal(),
                p.getFechaCreacion().truncatedTo(ChronoUnit.SECONDS).toString(),
                detalles);
    }
}
