package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.request.CrearPedidoRequest;
import com.casajojoapp.backend.dto.response.PedidoResponse;
import com.casajojoapp.backend.dto.response.ResumenCocinaResponse;
import com.casajojoapp.backend.enums.EstadoPedido;

import java.util.List;

public interface PedidoService {

    PedidoResponse crearPedido(CrearPedidoRequest request);

    PedidoResponse obtenerPedido(Long id);

    PedidoResponse obtenerPorCodigoSeguimiento(String codigo);

    /** Todos los pedidos (o solo los de un estado), por hora de retiro. */
    List<PedidoResponse> listarPedidos(String estado);

    /** Cola FIFO de cocina: pedidos RECIBIDO y EN_PREPARACION, por hora de retiro ascendente. */
    List<PedidoResponse> obtenerColaCocina();

    ResumenCocinaResponse obtenerResumenCocina();

    PedidoResponse actualizarEstado(Long id, EstadoPedido nuevoEstado);
}
