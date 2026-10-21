package com.casajojoapp.backend.dto.response;

import java.math.BigDecimal;

public record ItemMenuResponse(
        Long id,
        String categoria,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Boolean disponible) {
}
