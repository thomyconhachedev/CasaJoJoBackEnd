package com.casajojoapp.backend.repository;

import com.casajojoapp.backend.entity.Pedido;
import com.casajojoapp.backend.enums.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByCodigoSeguimiento(String codigoSeguimiento);

    boolean existsByCodigoSeguimiento(String codigoSeguimiento);

    List<Pedido> findAllByOrderByHoraRetiroAscIdAsc();

    List<Pedido> findByEstadoOrderByHoraRetiroAscIdAsc(EstadoPedido estado);

    List<Pedido> findByEstadoInOrderByHoraRetiroAscIdAsc(Collection<EstadoPedido> estados);
}
