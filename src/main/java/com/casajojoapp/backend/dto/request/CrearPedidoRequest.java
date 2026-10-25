package com.casajojoapp.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearPedidoRequest(
        Long usuarioId,

        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 100, message = "El nombre del cliente no puede superar 100 caracteres")
        String nombreCliente,

        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        String telefonoContacto,

        @NotBlank(message = "La hora de retiro es obligatoria")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "La hora de retiro debe tener formato HH:mm, por ejemplo 13:30")
        String horaRetiroSolicitada,

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
        String observaciones,

        @NotEmpty(message = "El pedido debe incluir al menos un ítem")
        List<@Valid ItemPedidoRequest> items) {
}
