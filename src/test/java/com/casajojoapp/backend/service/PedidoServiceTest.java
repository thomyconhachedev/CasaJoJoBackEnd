package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.request.CrearPedidoRequest;
import com.casajojoapp.backend.dto.request.ItemPedidoRequest;
import com.casajojoapp.backend.dto.response.DetallePedidoResponse;
import com.casajojoapp.backend.dto.response.ItemCocinaResponse;
import com.casajojoapp.backend.dto.response.PedidoResponse;
import com.casajojoapp.backend.dto.response.ResumenCocinaResponse;
import com.casajojoapp.backend.enums.EstadoMenu;
import com.casajojoapp.backend.enums.EstadoPedido;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.exception.ResourceNotFoundException;
import com.casajojoapp.backend.repository.ItemMenuRepository;
import com.casajojoapp.backend.repository.MenuDiarioRepository;
import com.casajojoapp.backend.repository.PedidoRepository;
import com.casajojoapp.backend.support.TestSupportConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reloj fijo: lunes 19-oct-2026 10:00 (hora de Chile). El menu abre hoy con stock limitado. */
@SpringBootTest
@Import(TestSupportConfig.class)
class PedidoServiceTest {

    private static final String CSV_MENU = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible,Stock
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa,3500,TRUE,10
            2,ACOMPANAMIENTO,Arroz Primavera,Arroz con verduras,0,TRUE,10
            3,BEBIDA_POSTRE,Coca-Cola Zero 350ml,Bebida,1500,TRUE,2
            4,PROTEINA,Pescado Frito,Reineta frita,3800,FALSE,10
            """;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private ItemMenuRepository itemMenuRepository;

    @Autowired
    private MenuDiarioRepository menuDiarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TestSupportConfig.StubMenuCsvSource source;

    private Long idPollo;
    private Long idArroz;
    private Long idBebida;
    private Long idPescado;

    @BeforeEach
    void preparar() {
        pedidoRepository.deleteAll();
        itemMenuRepository.deleteAll();
        menuDiarioRepository.deleteAll();

        source.setContenido(CSV_MENU);
        menuService.sincronizarConGoogleSheets();

        idPollo = idDe("Pollo al Jugo");
        idArroz = idDe("Arroz Primavera");
        idBebida = idDe("Coca-Cola Zero 350ml");
        idPescado = idDe("Pescado Frito");
    }

    @Test
    void creaElPedidoCalculaElMontoDescuentaStockYGuardaLosPrecios() {
        PedidoResponse r = crear(pedido("13:30", item(idPollo, 2), item(idBebida, 1)));

        assertThat(r.id()).isNotNull();
        assertThat(r.estado()).isEqualTo("RECIBIDO");
        assertThat(r.codigoSeguimiento()).matches("CJ-[0-9A-F]{4,}");
        assertThat(r.horaRetiro()).isEqualTo("13:30");
        assertThat(r.nombreCliente()).isEqualTo("Juan Pérez");
        assertThat(r.observaciones()).isEqualTo("Sin sal");
        assertThat(r.montoTotal()).isEqualByComparingTo("8500");
        assertThat(r.fechaCreacion()).isEqualTo("2026-10-19T13:00:00Z");
        assertThat(r.detalles()).hasSize(2);
        assertThat(stockDe(idPollo)).isEqualTo(8);
        assertThat(stockDe(idBebida)).isEqualTo(1);
    }

    @Test
    void sumaLasCantidadesDeUnMismoItemRepetido() {
        PedidoResponse r = crear(pedido("13:30", item(idPollo, 1), item(idPollo, 2)));

        assertThat(r.detalles()).hasSize(1);
        assertThat(r.detalles().get(0).cantidad()).isEqualTo(3);
        assertThat(r.montoTotal()).isEqualByComparingTo("10500");
        assertThat(stockDe(idPollo)).isEqualTo(7);
    }

    @Test
    void conservaElPlatoIdPorLineaAunqueDosPlatosPidanElMismoProducto() {
        // Dos platos del mismo pedido: plato 1 = pollo + arroz, plato 2 = pollo + bebida.
        PedidoResponse r = crear(pedido("13:30",
                item(idPollo, 1, 1, "Sin sal"),
                item(idArroz, 1, 1, "Sin sal"),
                item(idPollo, 1, 2, null),
                item(idBebida, 1, 2, null)));

        // El mismo producto en platos distintos NO se fusiona: son 4 líneas.
        assertThat(r.detalles()).hasSize(4);
        assertThat(r.detalles())
                .extracting(d -> d.platoId(), DetallePedidoResponse::itemMenuId)
                .containsExactlyInAnyOrder(
                        tuple(1, idPollo), tuple(1, idArroz),
                        tuple(2, idPollo), tuple(2, idBebida));

        // La observación pertenece al plato 1 y no contamina al plato 2.
        DetallePedidoResponse polloPlato1 = r.detalles().stream()
                .filter(d -> idPollo.equals(d.itemMenuId()) && Integer.valueOf(1).equals(d.platoId()))
                .findFirst().orElseThrow();
        DetallePedidoResponse polloPlato2 = r.detalles().stream()
                .filter(d -> idPollo.equals(d.itemMenuId()) && Integer.valueOf(2).equals(d.platoId()))
                .findFirst().orElseThrow();
        assertThat(polloPlato1.observacion()).isEqualTo("Sin sal");
        assertThat(polloPlato2.observacion()).isNull();

        // El stock se descuenta por el total (2 pollos), una sola vez por línea.
        assertThat(stockDe(idPollo)).isEqualTo(8);
        assertThat(stockDe(idArroz)).isEqualTo(9);
    }

    @Test
    void elPrecioQuedaCongeladoAunqueElMenuCambie() {
        PedidoResponse creado = crear(pedido("13:30", item(idPollo, 1)));

        source.setContenido(CSV_MENU.replace("3500", "4000"));
        menuService.sincronizarConGoogleSheets();

        PedidoResponse releido = pedidoService.obtenerPedido(creado.id());
        assertThat(releido.detalles().get(0).precioUnitario()).isEqualByComparingTo("3500");
        assertThat(releido.montoTotal()).isEqualByComparingTo("3500");
    }

    @Test
    void rechazaUnaHoraConMenosDe20MinutosDeAnticipacion() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> crear(pedido("10:19", item(idPollo, 1))));

        assertThat(e.getStatus()).isEqualTo(422);
        assertThat(e.getMessage()).contains("20 minutos");
        assertThat(stockDe(idPollo)).isEqualTo(10);

        // Justo en el limite (10:00 + 20 min) si se acepta.
        assertThat(crear(pedido("10:20", item(idPollo, 1))).horaRetiro()).isEqualTo("10:20");
    }

    @Test
    void rechazaUnaHoraConFormatoInvalido() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> crear(pedido("25:99", item(idPollo, 1))));

        assertThat(e.getStatus()).isEqualTo(400);
    }

    @Test
    void rechazaUnPedidoSinItems() {
        CrearPedidoRequest sinItems = new CrearPedidoRequest(null, "Juan Pérez", "", "13:30", null, List.of());

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> crear(sinItems));

        assertThat(e.getStatus()).isEqualTo(400);
    }

    @Test
    void rechazaPedidosConElMenuCerrado() {
        menuService.cambiarEstadoMenu(EstadoMenu.CERRADO);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> crear(pedido("13:30", item(idPollo, 1))));

        assertThat(e.getStatus()).isEqualTo(422);
        assertThat(e.getMessage()).contains("cerrado");
        assertThat(stockDe(idPollo)).isEqualTo(10);
    }

    @Test
    void sinStockSuficienteNoSeGuardaNadaYSeRevierteElDescuento() {
        // Sin bloqueo por stock (decision del equipo): solo se valida
        // disponible/no disponible; la venta procede aunque baje de 0.
        PedidoResponse creado = crear(pedido("13:30", item(idPollo, 1), item(idBebida, 3)));
        assertThat(creado.id()).isNotNull();
        assertThat(stockDe(idPollo)).isEqualTo(9);
        assertThat(stockDe(idBebida)).isEqualTo(-1);
        assertThat(pedidoRepository.count()).isEqualTo(1);
    }

    @Test
    void rechazaUnItemNoDisponible() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> crear(pedido("13:30", item(idPescado, 1))));

        assertThat(e.getStatus()).isEqualTo(422);
        assertThat(e.getMessage()).contains("no está disponible");
    }

    @Test
    void rechazaUnItemInexistente() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> crear(pedido("13:30", item(999_999L, 1))));

        assertThat(e.getStatus()).isEqualTo(400);
    }

    @Test
    void laColaDeCocinaEsFifoPorHoraDeRetiro() {
        PedidoResponse tarde = crear(pedido("14:00", item(idPollo, 1)));
        PedidoResponse tempranoA = crear(pedido("13:00", item(idArroz, 1)));
        PedidoResponse tempranoB = crear(pedido("13:00", item(idArroz, 1)));

        assertThat(pedidoService.obtenerColaCocina())
                .extracting(PedidoResponse::id)
                .containsExactly(tempranoA.id(), tempranoB.id(), tarde.id());

        pedidoService.actualizarEstado(tempranoA.id(), EstadoPedido.EN_PREPARACION);
        assertThat(pedidoService.obtenerColaCocina())
                .extracting(PedidoResponse::id)
                .containsExactly(tempranoA.id(), tempranoB.id(), tarde.id());

        pedidoService.actualizarEstado(tempranoA.id(), EstadoPedido.LISTO_PARA_RETIRO);
        assertThat(pedidoService.obtenerColaCocina())
                .extracting(PedidoResponse::id)
                .containsExactly(tempranoB.id(), tarde.id());
    }

    @Test
    void laMaquinaDeEstadosSoloAvanzaDeAUno() {
        PedidoResponse p = crear(pedido("13:30", item(idPollo, 1)));

        BusinessRuleException salto = assertThrows(BusinessRuleException.class,
                () -> pedidoService.actualizarEstado(p.id(), EstadoPedido.LISTO_PARA_RETIRO));
        assertThat(salto.getStatus()).isEqualTo(409);

        assertThat(pedidoService.actualizarEstado(p.id(), EstadoPedido.EN_PREPARACION).estado())
                .isEqualTo("EN_PREPARACION");

        BusinessRuleException retroceso = assertThrows(BusinessRuleException.class,
                () -> pedidoService.actualizarEstado(p.id(), EstadoPedido.RECIBIDO));
        assertThat(retroceso.getStatus()).isEqualTo(409);

        assertThat(pedidoService.actualizarEstado(p.id(), EstadoPedido.LISTO_PARA_RETIRO).estado())
                .isEqualTo("LISTO_PARA_RETIRO");
        assertThat(pedidoService.actualizarEstado(p.id(), EstadoPedido.ENTREGADO).estado())
                .isEqualTo("ENTREGADO");

        BusinessRuleException final_ = assertThrows(BusinessRuleException.class,
                () -> pedidoService.actualizarEstado(p.id(), EstadoPedido.ENTREGADO));
        assertThat(final_.getStatus()).isEqualTo(409);
    }

    @Test
    void seConsultaPorCodigoDeSeguimiento() {
        PedidoResponse p = crear(pedido("13:30", item(idPollo, 1)));

        assertThat(pedidoService.obtenerPorCodigoSeguimiento(p.codigoSeguimiento().toLowerCase()).id())
                .isEqualTo(p.id());
        assertThrows(ResourceNotFoundException.class,
                () -> pedidoService.obtenerPorCodigoSeguimiento("CJ-ZZZZ"));
    }

    @Test
    void elResumenDeCocinaSumaLasCantidadesPorItem() {
        crear(pedido("13:30", item(idPollo, 2), item(idBebida, 1)));
        crear(pedido("13:45", item(idPollo, 1)));

        ResumenCocinaResponse resumen = pedidoService.obtenerResumenCocina();

        assertThat(resumen.totalPedidosActivos()).isEqualTo(2);
        assertThat(resumen.recibidos()).isEqualTo(2);
        assertThat(resumen.enPreparacion()).isZero();
        assertThat(resumen.items())
                .extracting(ItemCocinaResponse::nombreItem, ItemCocinaResponse::cantidadTotal)
                .containsExactly(tuple("Coca-Cola Zero 350ml", 1), tuple("Pollo al Jugo", 3));
    }

    /** Pedido de invitado: sin sesion, usuarioId null. */
    private PedidoResponse crear(CrearPedidoRequest request) {
        return pedidoService.crearPedido(request, null);
    }

    private CrearPedidoRequest pedido(String hora, ItemPedidoRequest... items) {
        return new CrearPedidoRequest(null, "Juan Pérez", "+56912345678", hora, "Sin sal", List.of(items));
    }

    private ItemPedidoRequest item(Long id, int cantidad) {
        return new ItemPedidoRequest(id, cantidad);
    }

    private ItemPedidoRequest item(Long id, int cantidad, int platoId, String observacion) {
        return new ItemPedidoRequest(id, cantidad, observacion, platoId);
    }

    private Long idDe(String nombre) {
        return itemMenuRepository.findAll().stream()
                .filter(i -> i.getNombre().equals(nombre))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private int stockDe(Long id) {
        return itemMenuRepository.findById(id).orElseThrow().getStockActual();
    }
}
