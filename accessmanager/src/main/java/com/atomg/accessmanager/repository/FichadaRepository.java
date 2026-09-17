package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Fichada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FichadaRepository extends JpaRepository<Fichada, Long> {

    List<Fichada> findByLegajoRelojAndFechaHoraBetweenOrderByFechaHoraAsc(String legajoReloj, LocalDateTime inicio, LocalDateTime fin);

    // =========================================================================
    // DASHBOARD - Fichadas del dia actual
    // =========================================================================

    /**
     * Retorna los legajoReloj DISTINTOS que tienen al menos una fichada en el dia
     * indicado, filtrados por los legajos que pertenecen a la empresa.
     */
    @Query("SELECT DISTINCT f.legajoReloj FROM Fichada f " +
           "WHERE CAST(f.fechaHora AS date) = :hoy " +
           "AND f.legajoReloj IN (:legajos)")
    List<String> findLegajosConFichadaHoy(@Param("hoy") LocalDate hoy,
                                           @Param("legajos") List<String> legajos);
}