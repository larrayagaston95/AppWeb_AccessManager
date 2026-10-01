package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.dto.ResumenSectorDTO;
import com.atomg.accessmanager.model.ReporteAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReporteAsistenciaRepository extends JpaRepository<ReporteAsistencia, Long> {

    // ── 1. REPORTE MASIVO (detalle día a día de todos los empleados del sector) ──
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.sector.sucursal.empresa.id = :empresaId " +
            "AND r.empleado.sector.sucursal.idsucursal = :sucursalId " +
            "AND r.empleado.sector.id = :sectorId " +
            "AND YEAR(r.fecha) = :anio AND MONTH(r.fecha) = :mes " +
            "ORDER BY r.empleado.legajoReloj ASC, r.fecha ASC")
    List<ReporteAsistencia> buscarReportesMasivos(
            @Param("empresaId")   Long      empresaId,
            @Param("sucursalId")  Long      sucursalId,
            @Param("sectorId")    Long      sectorId,
            @Param("anio")        int       anio,
            @Param("mes")         int       mes
    );

    // ── 2. REPORTE INDIVIDUAL (día a día de un empleado) ──────────────────────
    /**
     * Recorrido del dato:
     * 1. El Controlador recibe la peticion de detalle de asistencia y extrae el identificador de la empresa.
     * 2. El Servicio invoca esta consulta pasandole ambos parametros.
     * 3. Se retorna la lista de reportes, aislando estrictamente la informacion por inquilino (tenant).
     */
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.legajoReloj = :legajo " +
            "AND r.empleado.empresaId = :empresaId " +
            "AND YEAR(r.fecha) = :anio AND MONTH(r.fecha) = :mes " +
            "ORDER BY r.fecha ASC")
    List<ReporteAsistencia> buscarReporteIndividual(
            @Param("legajo")      String    legajo,
            @Param("empresaId")   Long      empresaId,
            @Param("anio")        int       anio,
            @Param("mes")         int       mes
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
            "AND YEAR(r.fecha) = :anio AND MONTH(r.fecha) = :mes " +
            "GROUP BY r.empleado.legajoReloj, r.empleado.nombre, r.empleado.apellido " +
            "ORDER BY r.empleado.legajoReloj ASC")
    List<ResumenSectorDTO> obtenerResumenPorSector(
            @Param("sectorId")    Long      sectorId,
            @Param("anio")        int       anio,
            @Param("mes")         int       mes
    );

    // ── 4. BÚSQUEDA PARA EMPAREJAMIENTO DINÁMICO (por día exacto) ───────────────
    // Se usa @Query explícita para evitar ambigüedad en la resolución de Spring Data.
    // La cláusula "r.empleado.id = :empleadoId AND r.fecha = :fecha" es el único
    // filtro que garantiza un reporte único por (empleado, día).
    @Query("SELECT r FROM ReporteAsistencia r " +
            "WHERE r.empleado.id = :empleadoId " +
            "AND r.fecha = :fecha")
    Optional<ReporteAsistencia> buscarPorEmpleadoYFecha(
            @Param("empleadoId") Long empleadoId,
            @Param("fecha")      LocalDate fecha
    );
}