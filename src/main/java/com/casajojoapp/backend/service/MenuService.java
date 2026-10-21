package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.response.MenuDiarioResponse;
import com.casajojoapp.backend.entity.ItemMenu;
import com.casajojoapp.backend.enums.EstadoMenu;

public interface MenuService {

    /** Lee el Sheet y actualiza la copia local en MySQL (precios, disponibilidad y stock del dia). */
    MenuDiarioResponse sincronizarConGoogleSheets();

    /** Menu de hoy tal como esta en MySQL. No consulta Google Sheets. */
    MenuDiarioResponse obtenerMenuVigente();

    /** Abre o cierra la recepcion de pedidos del dia. */
    MenuDiarioResponse cambiarEstadoMenu(EstadoMenu estado);

    /** Descuenta stock con bloqueo pesimista. Debe llamarse dentro de la transaccion del pedido. */
    ItemMenu descontarStock(Long itemId, int cantidad);
}
