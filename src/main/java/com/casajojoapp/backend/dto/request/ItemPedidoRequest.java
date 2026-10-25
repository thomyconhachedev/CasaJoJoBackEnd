package com.casajojoapp.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemPedidoRequest(
        @NotNull(message = "itemMenuId es obligatorio")
        Long itemMenuId,

        @NotNull(message = "cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer cantidad,

        String observacion,

        /**
         * Indice del plato al que pertenece la linea (1..N dentro del pedido).
         * La app Android lo envia para que cocina vea cada combinacion por
         * separado; null cuando el cliente no lo manda (compatibilidad).
         */
        Integer platoId
) {
    public ItemPedidoRequest(Long itemMenuId, Integer cantidad) {
        this(itemMenuId, cantidad, null, null);
    }

    public ItemPedidoRequest(Long itemMenuId, Integer cantidad, String observacion) {
        this(itemMenuId, cantidad, observacion, null);
    }
}
