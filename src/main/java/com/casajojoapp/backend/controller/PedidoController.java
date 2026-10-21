package com.casajojoapp.backend.controller;

import com.casajojoapp.backend.dto.request.CambioEstadoRequest;
import com.casajojoapp.backend.dto.request.CrearPedidoRequest;
import com.casajojoapp.backend.dto.response.PedidoResponse;
import com.casajojoapp.backend.dto.response.ResumenCocinaResponse;
import com.casajojoapp.backend.enums.EstadoPedido;
import com.casajojoapp.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Solo valida el JSON y traduce a codigos HTTP; las reglas viven en PedidoService. */
@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crearPedido(request));
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponse>> listar(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(pedidoService.listarPedidos(estado));
    }

    @GetMapping("/cola")
    public ResponseEntity<List<PedidoResponse>> colaCocina() {
        return ResponseEntity.ok(pedidoService.obtenerColaCocina());
    }

    @GetMapping("/resumen-cocina")
    public ResponseEntity<ResumenCocinaResponse> resumenCocina() {
        return ResponseEntity.ok(pedidoService.obtenerResumenCocina());
    }

    @GetMapping("/seguimiento/{codigo}")
    public ResponseEntity<PedidoResponse> seguimiento(@PathVariable String codigo) {
        return ResponseEntity.ok(pedidoService.obtenerPorCodigoSeguimiento(codigo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerPedido(id));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoResponse> cambiarEstado(@PathVariable Long id,
                                                        @Valid @RequestBody CambioEstadoRequest request) {
        return ResponseEntity.ok(pedidoService.actualizarEstado(id, EstadoPedido.desde(request.estado())));
    }
}
