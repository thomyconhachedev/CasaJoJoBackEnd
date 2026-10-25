package com.casajojoapp.backend.dto.response;

import java.math.BigDecimal;

public record DetallePedidoResponse(
        Long itemMenuId,
        String nombreItem,
        Integer cantidad,
        BigDecimal precioUnitario,
        String observacion) {
}
