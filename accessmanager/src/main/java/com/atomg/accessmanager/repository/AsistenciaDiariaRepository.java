package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.AsistenciaDiaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaDiariaRepository extends JpaRepository<AsistenciaDiaria, Long> {
    Optional<AsistenciaDiaria> findByLegajoRelojAndFecha(String legajoReloj, LocalDate fecha);
    List<AsistenciaDiaria> findByIdEmpresaAndFechaBetweenOrderByFechaDesc(Long idEmpresa, LocalDate inicio, LocalDate fin);
}