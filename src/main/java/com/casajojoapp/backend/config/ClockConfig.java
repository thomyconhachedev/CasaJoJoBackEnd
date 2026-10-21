package com.casajojoapp.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj del negocio. Se inyecta en los servicios en vez de llamar a LocalDateTime.now(),
 * asi la hora de retiro y el cambio de dia del menu siguen la zona horaria de Chile
 * aunque el contenedor corra en UTC, y los tests pueden fijar la hora.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.zona-horaria:America/Santiago}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
