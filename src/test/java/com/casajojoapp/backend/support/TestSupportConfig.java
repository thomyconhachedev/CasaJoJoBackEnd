package com.casajojoapp.backend.support;

import com.casajojoapp.backend.source.MenuCsvSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Configuracion comun de los tests de integracion: un Sheet falso que se controla desde el test
 * y un reloj fijo en lunes 19-oct-2026, 10:00 hora de Chile (UTC-3).
 */
@TestConfiguration
public class TestSupportConfig {

    public static final Instant AHORA = Instant.parse("2026-10-19T13:00:00Z");

    @Bean
    @Primary
    public StubMenuCsvSource stubMenuCsvSource() {
        return new StubMenuCsvSource();
    }

    @Bean
    @Primary
    public Clock relojFijo() {
        return Clock.fixed(AHORA, ZoneOffset.ofHours(-3));
    }

    public static class StubMenuCsvSource implements MenuCsvSource {
        private String contenido;

        public void setContenido(String contenido) {
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
