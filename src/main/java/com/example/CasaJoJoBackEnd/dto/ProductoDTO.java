package com.example.CasaJoJoBackEnd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoDTO {
    private Long id;
    private String categoria;
    private String nombre;
    private String descripcion;
    private Integer precioExtra;
    private Boolean disponible;
}