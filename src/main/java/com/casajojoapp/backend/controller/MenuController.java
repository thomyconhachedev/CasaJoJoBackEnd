package com.casajojoapp.backend.controller;

import com.casajojoapp.backend.dto.request.CambioEstadoRequest;
import com.casajojoapp.backend.dto.response.MenuDiarioResponse;
import com.casajojoapp.backend.enums.EstadoMenu;
import com.casajojoapp.backend.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ResponseEntity<MenuDiarioResponse> obtenerMenuVigente() {
        return ResponseEntity.ok(menuService.obtenerMenuVigente());
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<MenuDiarioResponse> sincronizar() {
        return ResponseEntity.ok(menuService.sincronizarConGoogleSheets());
    }

    @PatchMapping("/estado")
    public ResponseEntity<MenuDiarioResponse> cambiarEstado(@Valid @RequestBody CambioEstadoRequest request) {
        return ResponseEntity.ok(menuService.cambiarEstadoMenu(EstadoMenu.desde(request.estado())));
    }
}
