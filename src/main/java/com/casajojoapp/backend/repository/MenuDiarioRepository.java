package com.casajojoapp.backend.repository;

import com.casajojoapp.backend.entity.MenuDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MenuDiarioRepository extends JpaRepository<MenuDiario, Long> {

    Optional<MenuDiario> findByFecha(LocalDate fecha);
}
