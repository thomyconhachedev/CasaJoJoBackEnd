package com.example.CasaJoJoBackEnd.controller;

import com.example.CasaJoJoBackEnd.dto.ProductoDTO;
import com.example.CasaJoJoBackEnd.exception.SincronizacionException;
import com.example.CasaJoJoBackEnd.service.MenuService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/productos")
    public ResponseEntity<List<ProductoDTO>> obtenerProductos() {
        return ResponseEntity.ok(menuService.obtenerProductos());
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<?> sincronizarMenu() {
        try {
            return ResponseEntity.ok(menuService.sincronizarDesdeSheets());
        } catch (SincronizacionException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}