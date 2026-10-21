package com.casajojoapp.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CambioEstadoRequest(
        @NotBlank(message = "El estado es obligatorio")
        String estado) {
}
