package com.example.CasaJoJoBackEnd.service;

import com.example.CasaJoJoBackEnd.dto.ProductoDTO;
import com.example.CasaJoJoBackEnd.model.Producto;
import com.example.CasaJoJoBackEnd.repository.IProductoRepository;
import com.example.CasaJoJoBackEnd.source.MenuCsvSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MenuServiceSyncTest {

    private static final String CSV_TRES_PRODUCTOS = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa al oregano,0,TRUE
            2,PROTEINA,Carne Mechada,Vacuno tierno,500,TRUE
            3,ENSALADA,Ensalada Mixta,Lechuga y tomate,0,TRUE
            """;

    private static final String CSV_DOS_PRODUCTOS_PRECIO_CAMBIADO = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa al oregano,999,TRUE
            3,ENSALADA,Ensalada Mixta,Lechuga y tomate,0,FALSE
            """;

    private static final String CSV_SIN_COLUMNA_ID = """
            Categoria,Nombre,Descripcion,PrecioExtra,Disponible
            PROTEINA,Pollo al Jugo,Pechuga jugosa,0,TRUE
            ENSALADA,Ensalada Mixta,Lechuga y tomate,0,TRUE
            """;

    @TestConfiguration
    static class StubCsvSourceConfig {
        @Bean
        @Primary
        StubMenuCsvSource stubMenuCsvSource() {
            return new StubMenuCsvSource();
        }
    }

    @Autowired
    private MenuService menuService;

    @Autowired
    private IProductoRepository repository;

    @Autowired
    private StubMenuCsvSource source;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    @Test
    void guardaLoQueLeeDelSheet() {
        source.setContenido(CSV_TRES_PRODUCTOS);

        List<ProductoDTO> guardados = menuService.sincronizarDesdeSheets();

        assertThat(guardados).hasSize(3);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findAll())
                .extracting(Producto::getCodigoExterno)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void repiteLosMismosIdsEnSincronizacionesSucesivas() {
        source.setContenido(CSV_TRES_PRODUCTOS);
        List<Long> idsPrimera = idsDe(menuService.sincronizarDesdeSheets());

        source.setContenido(CSV_TRES_PRODUCTOS);
        List<Long> idsSegunda = idsDe(menuService.sincronizarDesdeSheets());

        assertThat(idsSegunda).containsExactlyElementsOf(idsPrimera);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void actualizaYPrecioYBorraLosQueYaNoEstanEnElSheet() {
        source.setContenido(CSV_TRES_PRODUCTOS);
        menuService.sincronizarDesdeSheets();
        Long idPollo = idPorCodigo(1L);
        Long idCarne = idPorCodigo(2L);

        source.setContenido(CSV_DOS_PRODUCTOS_PRECIO_CAMBIADO);
        menuService.sincronizarDesdeSheets();

        assertThat(repository.count()).isEqualTo(2);
        assertThat(idPorCodigo(1L)).isEqualTo(idPollo);
        assertThat(buscarPorCodigo(1L).getPrecioExtra()).isEqualTo(999);
        assertThat(buscarPorCodigo(3L).getDisponible()).isFalse();
        assertThat(idPorCodigo(2L)).isNull();
        assertThat(repository.findById(idCarne)).isEmpty();
    }

    @Test
    void mantieneLosIdsAunqueSeBorreLaColumnaIdDelSheet() {
        source.setContenido(CSV_SIN_COLUMNA_ID);
        List<Long> idsPrimera = idsDe(menuService.sincronizarDesdeSheets());
        assertThat(repository.findAll()).allSatisfy(p -> assertThat(p.getCodigoExterno()).isNull());

        source.setContenido(CSV_SIN_COLUMNA_ID);
        List<Long> idsSegunda = idsDe(menuService.sincronizarDesdeSheets());

        assertThat(idsSegunda).containsExactlyElementsOf(idsPrimera);
        assertThat(repository.count()).isEqualTo(2);
    }

    private List<Long> idsDe(List<ProductoDTO> productos) {
        return productos.stream().map(ProductoDTO::getId).toList();
    }

    private Producto buscarPorCodigo(Long codigo) {
        return repository.findAll().stream()
                .filter(p -> Objects.equals(p.getCodigoExterno(), codigo))
                .findFirst()
                .orElse(null);
    }

    private Long idPorCodigo(Long codigo) {
        Producto producto = buscarPorCodigo(codigo);
        return producto == null ? null : producto.getId();
    }

    static class StubMenuCsvSource implements MenuCsvSource {
        private String contenido;

        void setContenido(String contenido) {
            this.contenido = contenido;
        }

        @Override
        public String descargar() {
            if (contenido == null) {
                throw new IllegalStateException("El test no configuró el contenido del CSV.");
            }
            return contenido;
        }
    }
}
