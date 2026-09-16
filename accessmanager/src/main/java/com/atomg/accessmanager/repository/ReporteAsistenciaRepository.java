package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.dto.ResumenSectorDTO;
import com.atomg.accessmanager.model.ReporteAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReporteAsistenciaRepository extends JpaRepository<ReporteAsistencia, Long> {

    // ── 1. REPORTE MASIVO (detalle día a día de todos los empleados del sector) ──
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.sector.sucursal.empresa.id = :empresaId " +
            "AND r.empleado.sector.sucursal.idsucursal = :sucursalId " +
            "AND r.empleado.sector.id = :sectorId " +
            "AND r.fecha BETWEEN :fechaInicio AND :fechaFin " +
            "ORDER BY r.empleado.legajoReloj ASC, r.fecha ASC")
    List<ReporteAsistencia> buscarReportesMasivos(
            @Param("empresaId")   Long      empresaId,
            @Param("sucursalId")  Long      sucursalId,
            @Param("sectorId")    Long      sectorId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin")    LocalDate fechaFin
    );

    // ── 2. REPORTE INDIVIDUAL (día a día de un empleado) ──────────────────────
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.legajoReloj = :legajo " +
            "AND r.fecha BETWEEN :fechaInicio AND :fechaFin " +
            "ORDER BY r.fecha ASC")
    List<ReporteAsistencia> buscarReporteIndividual(
            @Param("legajo")      String    legajo,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin")    LocalDate fechaFin
    );

    // ── 3. RESUMEN AGREGADO POR SECTOR (un registro por empleado con totales) ─
    @Query("SELECT new com.atomg.accessmanager.dto.ResumenSectorDTO(" +
            "  r.empleado.legajoReloj, " +
            "  r.empleado.nombre, " +
            "  r.empleado.apellido, " +
            "  COUNT(r.id), " +
            "  COALESCE(SUM(r.horasTrabajadas), 0.0), " +
            "  COALESCE(SUM(r.horasExtras), 0.0) " +
            ") " +
            "FROM ReporteAsistencia r " +
            "WHERE r.empleado.sector.id = :sectorId " +
            "AND r.fecha BETWEEN :fechaInicio AND :fechaFin " +
            "GROUP BY r.empleado.legajoReloj, r.empleado.nombre, r.empleado.apellido " +
            "ORDER BY r.empleado.legajoReloj ASC")
    List<ResumenSectorDTO> obtenerResumenPorSector(
            @Param("sectorId")    Long      sectorId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin")    LocalDate fechaFin
    );
}