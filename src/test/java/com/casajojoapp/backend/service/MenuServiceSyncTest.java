package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.response.MenuDiarioResponse;
import com.casajojoapp.backend.entity.ItemMenu;
import com.casajojoapp.backend.entity.MenuDiario;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.repository.ItemMenuRepository;
import com.casajojoapp.backend.repository.MenuDiarioRepository;
import com.casajojoapp.backend.repository.PedidoRepository;
import com.casajojoapp.backend.support.TestSupportConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestSupportConfig.class)
class MenuServiceSyncTest {

    private static final String CSV_TRES_ITEMS = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible,Stock
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa al oregano,0,TRUE,10
            2,PROTEINA,Carne Mechada,Vacuno tierno,500,TRUE,5
            3,ENSALADA,Ensalada Mixta,Lechuga y tomate,0,TRUE,8
            """;

    private static final String CSV_DOS_ITEMS_PRECIO_CAMBIADO = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible,Stock
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa al oregano,999,TRUE,10
            3,ENSALADA,Ensalada Mixta,Lechuga y tomate,0,FALSE,8
            """;

    private static final String CSV_STOCK_AMPLIADO = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible,Stock
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa al oregano,0,TRUE,20
            2,PROTEINA,Carne Mechada,Vacuno tierno,500,TRUE,5
            3,ENSALADA,Ensalada Mixta,Lechuga y tomate,0,TRUE,8
            """;

    private static final String CSV_SIN_COLUMNA_ID = """
            Categoria,Nombre,Descripcion,PrecioExtra,Disponible
            PROTEINA,Pollo al Jugo,Pechuga jugosa,0,TRUE
            ENSALADA,Ensalada Mixta,Lechuga y tomate,0,TRUE
            """;

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

    @BeforeEach
    void limpiar() {
        pedidoRepository.deleteAll();
        itemMenuRepository.deleteAll();
        menuDiarioRepository.deleteAll();
    }

    @Test
    void guardaLoQueLeeDelSheetYAbreElMenuDeHoy() {
        source.setContenido(CSV_TRES_ITEMS);

        MenuDiarioResponse menu = menuService.sincronizarConGoogleSheets();

        assertThat(menu.fecha()).isEqualTo("2026-10-19");
        assertThat(menu.estado()).isEqualTo("ABIERTO");
        assertThat(menu.items()).hasSize(3);
        assertThat(itemMenuRepository.count()).isEqualTo(3);
        assertThat(itemMenuRepository.findAll())
                .extracting(ItemMenu::getCodigoExterno)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void repiteLosMismosIdsEnSincronizacionesSucesivas() {
        source.setContenido(CSV_TRES_ITEMS);
        List<Long> idsPrimera = idsDe(menuService.sincronizarConGoogleSheets());

        source.setContenido(CSV_TRES_ITEMS);
        List<Long> idsSegunda = idsDe(menuService.sincronizarConGoogleSheets());

        assertThat(idsSegunda).containsExactlyElementsOf(idsPrimera);
        assertThat(itemMenuRepository.count()).isEqualTo(3);
        assertThat(menuDiarioRepository.count()).isEqualTo(1);
    }

    @Test
    void actualizaPrecioYBorraLosQueYaNoEstanEnElSheet() {
        source.setContenido(CSV_TRES_ITEMS);
        menuService.sincronizarConGoogleSheets();
        Long idPollo = idPorCodigo(1L);
        Long idCarne = idPorCodigo(2L);

        source.setContenido(CSV_DOS_ITEMS_PRECIO_CAMBIADO);
        menuService.sincronizarConGoogleSheets();

        assertThat(itemMenuRepository.count()).isEqualTo(2);
        assertThat(idPorCodigo(1L)).isEqualTo(idPollo);
        assertThat(buscarPorCodigo(1L).getPrecio()).isEqualByComparingTo("999");
        assertThat(buscarPorCodigo(3L).getDisponible()).isFalse();
        assertThat(idPorCodigo(2L)).isNull();
        assertThat(itemMenuRepository.findById(idCarne)).isEmpty();
    }

    @Test
    void mantieneLosIdsAunqueSeBorreLaColumnaIdDelSheet() {
        source.setContenido(CSV_SIN_COLUMNA_ID);
        List<Long> idsPrimera = idsDe(menuService.sincronizarConGoogleSheets());
        assertThat(itemMenuRepository.findAll()).allSatisfy(i -> assertThat(i.getCodigoExterno()).isNull());

        source.setContenido(CSV_SIN_COLUMNA_ID);
        List<Long> idsSegunda = idsDe(menuService.sincronizarConGoogleSheets());

        assertThat(idsSegunda).containsExactlyElementsOf(idsPrimera);
        assertThat(itemMenuRepository.count()).isEqualTo(2);
    }

    @Test
    void usaElStockDelSheet() {
        source.setContenido(CSV_TRES_ITEMS);

        menuService.sincronizarConGoogleSheets();

        assertThat(buscarPorCodigo(1L).getStockInicial()).isEqualTo(10);
        assertThat(buscarPorCodigo(1L).getStockActual()).isEqualTo(10);
        assertThat(buscarPorCodigo(2L).getStockActual()).isEqualTo(5);
    }

    @Test
    void usaElStockPorDefectoSiElSheetNoTraeLaColumna() {
        source.setContenido(CSV_SIN_COLUMNA_ID);

        menuService.sincronizarConGoogleSheets();

        assertThat(itemMenuRepository.findAll()).allSatisfy(i -> {
            assertThat(i.getStockInicial()).isEqualTo(50);
            assertThat(i.getStockActual()).isEqualTo(50);
        });
    }

    @Test
    void reSincronizarElMismoDiaConservaLoYaVendido() {
        source.setContenido(CSV_TRES_ITEMS);
        menuService.sincronizarConGoogleSheets();
        menuService.descontarStock(idPorCodigo(1L), 3);
        assertThat(buscarPorCodigo(1L).getStockActual()).isEqualTo(7);

        source.setContenido(CSV_TRES_ITEMS);
        menuService.sincronizarConGoogleSheets();
        assertThat(buscarPorCodigo(1L).getStockActual()).isEqualTo(7);

        source.setContenido(CSV_STOCK_AMPLIADO);
        menuService.sincronizarConGoogleSheets();
        assertThat(buscarPorCodigo(1L).getStockInicial()).isEqualTo(20);
        assertThat(buscarPorCodigo(1L).getStockActual()).isEqualTo(17);
    }

    @Test
    void unDiaNuevoReiniciaElStock() {
        source.setContenido(CSV_TRES_ITEMS);
        menuService.sincronizarConGoogleSheets();
        menuService.descontarStock(idPorCodigo(1L), 3);

        MenuDiario hoy = menuDiarioRepository.findAll().get(0);
        hoy.setFecha(LocalDate.of(2026, 10, 18));
        menuDiarioRepository.save(hoy);

        source.setContenido(CSV_TRES_ITEMS);
        menuService.sincronizarConGoogleSheets();

        assertThat(menuDiarioRepository.count()).isEqualTo(2);
        assertThat(buscarPorCodigo(1L).getStockActual()).isEqualTo(10);
    }

    @Test
    void descontarStockRechazaSiNoHayStockSuficiente() {
        source.setContenido(CSV_TRES_ITEMS);
        menuService.sincronizarConGoogleSheets();
        Long idCarne = idPorCodigo(2L);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> menuService.descontarStock(idCarne, 6));

        assertThat(e.getStatus()).isEqualTo(409);
        assertThat(buscarPorCodigo(2L).getStockActual()).isEqualTo(5);
    }

    @Test
    void descontarStockRechazaUnItemNoDisponible() {
        source.setContenido(CSV_DOS_ITEMS_PRECIO_CAMBIADO);
        menuService.sincronizarConGoogleSheets();
        Long idEnsalada = idPorCodigo(3L);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> menuService.descontarStock(idEnsalada, 1));

        assertThat(e.getStatus()).isEqualTo(422);
    }

    private List<Long> idsDe(MenuDiarioResponse menu) {
        return menu.items().stream().map(i -> i.id()).toList();
    }

    private ItemMenu buscarPorCodigo(Long codigo) {
        return itemMenuRepository.findAll().stream()
                .filter(i -> Objects.equals(i.getCodigoExterno(), codigo))
                .findFirst()
                .orElse(null);
    }

    private Long idPorCodigo(Long codigo) {
        ItemMenu item = buscarPorCodigo(codigo);
        return item == null ? null : item.getId();
    }
}
