package com.casajojoapp.backend.dto.response;

import java.util.List;

public record MenuDiarioResponse(
        String fecha,
        String estado,
        List<ItemMenuResponse> items) {
}
