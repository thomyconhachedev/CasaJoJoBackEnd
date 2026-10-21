package com.casajojoapp.backend.dto.response;

import java.util.List;

public record ResumenCocinaResponse(
        Integer totalPedidosActivos,
        Integer recibidos,
        Integer enPreparacion,
        List<ItemCocinaResponse> items) {
}
