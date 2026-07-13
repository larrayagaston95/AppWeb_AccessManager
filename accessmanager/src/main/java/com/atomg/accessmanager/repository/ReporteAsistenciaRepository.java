package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.ReporteAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReporteAsistenciaRepository extends JpaRepository<ReporteAsistencia, Long> {

    // 📚 1. QUERY PARA REPORTE MASIVO
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.sector.sucursal.empresa.id = :empresaId " +
            "AND r.empleado.sector.sucursal.idsucursal = :sucursalId " +
            "AND r.empleado.sector.id = :sectorId " +
            "AND r.fecha BETWEEN :fechaInicio AND :fechaFin " +
            "ORDER BY r.empleado.legajoReloj ASC, r.fecha ASC")
    List<ReporteAsistencia> buscarReportesMasivos(
            @Param("empresaId") Long empresaId,
            @Param("sucursalId") Long sucursalId,
            @Param("sectorId") Long sectorId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin
    );

    // 📄 2. QUERY PARA REPORTE INDIVIDUAL
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.legajoReloj = :legajo " +
            "AND r.fecha BETWEEN :fechaInicio AND :fechaFin " +
            "ORDER BY r.fecha ASC")
    List<ReporteAsistencia> buscarReporteIndividual(
            @Param("legajo") String legajo,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin
    );
}