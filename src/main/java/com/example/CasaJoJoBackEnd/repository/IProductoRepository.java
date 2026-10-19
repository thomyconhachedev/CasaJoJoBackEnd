package com.example.CasaJoJoBackEnd.repository;

import com.example.CasaJoJoBackEnd.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByDisponibleTrue();
}
