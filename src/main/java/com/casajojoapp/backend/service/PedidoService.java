package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.request.CrearPedidoRequest;
import com.casajojoapp.backend.dto.response.PedidoResponse;
import com.casajojoapp.backend.dto.response.ResumenCocinaResponse;
import com.casajojoapp.backend.enums.EstadoPedido;

import java.util.List;

public interface PedidoService {

    /**
     * usuarioId es el de la sesion (token); null si el pedido es de un invitado.
     * El usuarioId que venga dentro del cuerpo de la peticion se ignora: nadie puede pedir a nombre de otro.
     */
    PedidoResponse crearPedido(CrearPedidoRequest request, Long usuarioId);

    PedidoResponse obtenerPedido(Long id);

    PedidoResponse obtenerPorCodigoSeguimiento(String codigo);

    /** Todos los pedidos (o solo los de un estado), por hora de retiro. */
    List<PedidoResponse> listarPedidos(String estado);

    /** Pedidos de una cuenta concreta (usuarioId del token), más recientes primero. */
    List<PedidoResponse> listarPedidosDeUsuario(Long usuarioId);

    /** Cola FIFO de cocina: pedidos RECIBIDO y EN_PREPARACION, por hora de retiro ascendente. */
    List<PedidoResponse> obtenerColaCocina();

    ResumenCocinaResponse obtenerResumenCocina();

    PedidoResponse actualizarEstado(Long id, EstadoPedido nuevoEstado);
}
