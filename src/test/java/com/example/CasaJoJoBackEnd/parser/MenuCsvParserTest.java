package com.example.CasaJoJoBackEnd.parser;

import com.example.CasaJoJoBackEnd.dto.ProductoImportado;
import com.example.CasaJoJoBackEnd.exception.SincronizacionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuCsvParserTest {

    private final MenuCsvParser parser = new MenuCsvParser();

    @Test
    void parseaCsvConEncabezadoYColumnaId() {
        String csv = """
                ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible
                1,PROTEINA,Pollo al Jugo,Pechuga jugosa al oregano,0,TRUE
                2,ACOMPANAMIENTO,Arroz Primavera,"Arroz con choclo, arvejas",500,FALSE
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.filasIgnoradas()).isZero();
        assertThat(resultado.productos()).hasSize(2);

        ProductoImportado pollo = resultado.productos().get(0);
        assertThat(pollo.codigoExterno()).isEqualTo(1L);
        assertThat(pollo.categoria()).isEqualTo("PROTEINA");
        assertThat(pollo.nombre()).isEqualTo("Pollo al Jugo");
        assertThat(pollo.descripcion()).isEqualTo("Pechuga jugosa al oregano");
        assertThat(pollo.precioExtra()).isZero();
        assertThat(pollo.disponible()).isTrue();

        ProductoImportado arroz = resultado.productos().get(1);
        assertThat(arroz.nombre()).isEqualTo("Arroz Primavera");
        assertThat(arroz.precioExtra()).isEqualTo(500);
        assertThat(arroz.disponible()).isFalse();
    }

    @Test
    void parseaCsvSinColumnaId() {
        String csv = """
                Categoria,Nombre,Descripcion,PrecioExtra,Disponible
                PROTEINA,Pollo al Jugo,Pechuga jugosa,0,TRUE
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos()).hasSize(1);
        assertThat(resultado.productos().get(0).codigoExterno()).isNull();
        assertThat(resultado.productos().get(0).nombre()).isEqualTo("Pollo al Jugo");
    }

    @Test
    void parseaColumnasEnCualquierOrden() {
        String csv = """
                Disponible,Nombre,ID,PrecioExtra,Categoria,Descripcion
                FALSE,Pescado Frito,7,800,PROTEINA,Reineta frita
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        ProductoImportado pescado = resultado.productos().get(0);
        assertThat(pescado.codigoExterno()).isEqualTo(7L);
        assertThat(pescado.categoria()).isEqualTo("PROTEINA");
        assertThat(pescado.nombre()).isEqualTo("Pescado Frito");
        assertThat(pescado.descripcion()).isEqualTo("Reineta frita");
        assertThat(pescado.precioExtra()).isEqualTo(800);
        assertThat(pescado.disponible()).isFalse();
    }

    @Test
    void ignoraFilasSinNombre() {
        String csv = """
                ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible
                1,PROTEINA,,Sin nombre,0,TRUE
                2,PROTEINA,Pollo al Jugo,,0,TRUE
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos()).hasSize(1);
        assertThat(resultado.filasIgnoradas()).isEqualTo(1);
    }

    @Test
    void limpiaElFormatoDelPrecio() {
        String csv = """
                Categoria,Nombre,Precio
                PROTEINA,Carne Mechada,$1.500
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos().get(0).precioExtra()).isEqualTo(1500);
    }

    @Test
    void disponibleVacioEsTrue() {
        String csv = """
                Categoria,Nombre,Disponible
                PROTEINA,Pollo al Jugo,
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos().get(0).disponible()).isTrue();
    }

    @Test
    void categoriaVaciaEsGeneral() {
        String csv = """
                Nombre,Categoria
                Pollo al Jugo,
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos().get(0).categoria()).isEqualTo("GENERAL");
    }

    @Test
    void ignoraCodigosExternosDuplicados() {
        String csv = """
                ID,Categoria,Nombre
                5,PROTEINA,Pollo al Jugo
                5,PROTEINA,Otro Producto
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos()).hasSize(1);
        assertThat(resultado.filasIgnoradas()).isEqualTo(1);
    }

    @Test
    void fallaSiSoloHayEncabezado() {
        String csv = """
                ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible
                """;

        assertThatThrownBy(() -> parser.parsear(csv))
                .isInstanceOf(SincronizacionException.class);
    }

    @Test
    void usaPosicionesSiNoHayEncabezado() {
        String csv = """
                1,PROTEINA,Pollo al Jugo,Pechuga jugosa,0,TRUE
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.productos()).hasSize(1);
        assertThat(resultado.productos().get(0).nombre()).isEqualTo("Pollo al Jugo");
    }
}
