package com.casajojoapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Linea de un pedido. Guarda el nombre y el precio del momento (precio_historico): el menu cambia
 * cada dia y la sincronizacion borra los items que ya no estan en el Sheet, por eso item_menu_id
 * no es una clave foranea.
 */
@Entity
@Table(name = "pedido_detalle")
@Getter
@Setter
@NoArgsConstructor
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "item_menu_id")
    private Long itemMenuId;

    @Column(name = "nombre_item", nullable = false, length = 100)
    private String nombreItem;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_historico", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioHistorico;
}
