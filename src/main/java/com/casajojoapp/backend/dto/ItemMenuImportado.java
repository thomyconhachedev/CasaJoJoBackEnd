package com.casajojoapp.backend.dto;

/** Una fila del Sheet ya interpretada. stock es null si el Sheet no trae esa columna. */
public record ItemMenuImportado(
        Long codigoExterno,
        String categoria,
        String nombre,
        String descripcion,
        Integer precio,
        Boolean disponible,
        Integer stock) {
}
