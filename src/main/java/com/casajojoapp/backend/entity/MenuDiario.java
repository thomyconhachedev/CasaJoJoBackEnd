package com.casajojoapp.backend.entity;

import com.casajojoapp.backend.enums.EstadoMenu;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Estado operativo del menu de un dia: si recibe pedidos (ABIERTO) o no (CERRADO). */
@Entity
@Table(name = "menu_diario")
@Getter
@Setter
@NoArgsConstructor
public class MenuDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMenu estado = EstadoMenu.ABIERTO;

    @Column(name = "ultima_sincronizacion")
    private LocalDateTime ultimaSincronizacion;
}
