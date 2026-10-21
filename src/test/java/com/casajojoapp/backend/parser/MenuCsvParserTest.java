package com.casajojoapp.backend.parser;

import com.casajojoapp.backend.dto.ItemMenuImportado;
import com.casajojoapp.backend.exception.SincronizacionException;
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
        assertThat(resultado.items()).hasSize(2);

        ItemMenuImportado pollo = resultado.items().get(0);
        assertThat(pollo.codigoExterno()).isEqualTo(1L);
        assertThat(pollo.categoria()).isEqualTo("PROTEINA");
        assertThat(pollo.nombre()).isEqualTo("Pollo al Jugo");
        assertThat(pollo.descripcion()).isEqualTo("Pechuga jugosa al oregano");
        assertThat(pollo.precio()).isZero();
        assertThat(pollo.disponible()).isTrue();

        ItemMenuImportado arroz = resultado.items().get(1);
        assertThat(arroz.nombre()).isEqualTo("Arroz Primavera");
        assertThat(arroz.precio()).isEqualTo(500);
        assertThat(arroz.disponible()).isFalse();
    }

    @Test
    void parseaCsvSinColumnaId() {
        String csv = """
                Categoria,Nombre,Descripcion,PrecioExtra,Disponible
                PROTEINA,Pollo al Jugo,Pechuga jugosa,0,TRUE
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.items()).hasSize(1);
        assertThat(resultado.items().get(0).codigoExterno()).isNull();
        assertThat(resultado.items().get(0).nombre()).isEqualTo("Pollo al Jugo");
    }

    @Test
    void parseaColumnasEnCualquierOrden() {
        String csv = """
                Disponible,Nombre,ID,PrecioExtra,Categoria,Descripcion
                FALSE,Pescado Frito,7,800,PROTEINA,Reineta frita
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        ItemMenuImportado pescado = resultado.items().get(0);
        assertThat(pescado.codigoExterno()).isEqualTo(7L);
        assertThat(pescado.categoria()).isEqualTo("PROTEINA");
        assertThat(pescado.nombre()).isEqualTo("Pescado Frito");
        assertThat(pescado.descripcion()).isEqualTo("Reineta frita");
        assertThat(pescado.precio()).isEqualTo(800);
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

        assertThat(resultado.items()).hasSize(1);
        assertThat(resultado.filasIgnoradas()).isEqualTo(1);
    }

    @Test
    void limpiaElFormatoDelPrecio() {
        String csv = """
                Categoria,Nombre,Precio
                PROTEINA,Carne Mechada,$1.500
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.items().get(0).precio()).isEqualTo(1500);
    }

    @Test
    void disponibleVacioEsTrue() {
        String csv = """
                Categoria,Nombre,Disponible
                PROTEINA,Pollo al Jugo,
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.items().get(0).disponible()).isTrue();
    }

    @Test
    void categoriaVaciaEsGeneral() {
        String csv = """
                Nombre,Categoria
                Pollo al Jugo,
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.items().get(0).categoria()).isEqualTo("GENERAL");
    }

    @Test
    void ignoraCodigosExternosDuplicados() {
        String csv = """
                ID,Categoria,Nombre
                5,PROTEINA,Pollo al Jugo
                5,PROTEINA,Otro Producto
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.items()).hasSize(1);
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

        assertThat(resultado.items()).hasSize(1);
        assertThat(resultado.items().get(0).nombre()).isEqualTo("Pollo al Jugo");
    }

    @Test
    void leeLaColumnaStock() {
        String csv = """
                Categoria,Nombre,PrecioExtra,Disponible,Stock
                PROTEINA,Pollo al Jugo,0,TRUE,12
                """;

        MenuCsvParser.Resultado resultado = parser.parsear(csv);

        assertThat(resultado.items().get(0).stock()).isEqualTo(12);
    }

    @Test
    void stockVacioOSinColumnaEsNull() {
        String conColumnaVacia = """
                Categoria,Nombre,Stock
                PROTEINA,Pollo al Jugo,
                """;
        String sinColumna = """
                Categoria,Nombre
                PROTEINA,Pollo al Jugo
                """;

        assertThat(parser.parsear(conColumnaVacia).items().get(0).stock()).isNull();
        assertThat(parser.parsear(sinColumna).items().get(0).stock()).isNull();
    }
}
