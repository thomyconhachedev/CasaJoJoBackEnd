package com.casajojoapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Copia local (MySQL) de un producto del Sheet, con el stock del dia. */
@Entity
@Table(name = "item_menu")
@Getter
@Setter
@NoArgsConstructor
public class ItemMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_externo", unique = true)
    private Long codigoExterno;

    @Column(nullable = false, length = 50)
    private String categoria;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    @Column(name = "stock_inicial", nullable = false)
    private Integer stockInicial = 0;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    @Column(nullable = false)
    private Boolean disponible = true;
}
