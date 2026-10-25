package com.casajojoapp.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PedidoResponse(
        Long id,
        String codigoSeguimiento,
        Long usuarioId,
        String estado,
        String horaRetiro,
        String nombreCliente,
        String telefonoContacto,
        String observaciones,
        BigDecimal montoTotal,
        String fechaCreacion,
        List<DetallePedidoResponse> detalles) {
}
