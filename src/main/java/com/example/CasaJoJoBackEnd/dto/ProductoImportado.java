package com.example.CasaJoJoBackEnd.dto;

public record ProductoImportado(
        Long codigoExterno,
        String categoria,
        String nombre,
        String descripcion,
        Integer precioExtra,
        Boolean disponible) {
}
