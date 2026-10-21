package com.casajojoapp.backend.repository;

import com.casajojoapp.backend.entity.ItemMenu;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemMenuRepository extends JpaRepository<ItemMenu, Long> {

    /** Bloqueo pesimista: dos pedidos simultaneos no pueden descontar el mismo stock a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ItemMenu> findWithLockById(Long id);
}
