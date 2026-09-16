package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Licencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LicenciaRepository extends JpaRepository<Licencia, Long> {

    /** Lista todas las licencias de un empleado (filtrado por empresa para multi-tenant). */
    List<Licencia> findByEmpleadoIdAndEmpresaId(Long empleadoId, Long empresaId);

    /**
     * Busca si un empleado tiene una licencia vigente en una fecha especifica.
     * Retorna la primera licencia cuyo rango [fechaInicio, fechaFin] contenga la fecha dada.
     * Se usa en AsistenciaService para enriquecer el campo "observaciones" del reporte.
     */
    @Query("SELECT l FROM Licencia l " +
           "WHERE l.empleado.id = :empleadoId " +
           "AND :fecha BETWEEN l.fechaInicio AND l.fechaFin")
    Optional<Licencia> findLicenciaActivaEnFecha(
            @Param("empleadoId") Long empleadoId,
            @Param("fecha")      LocalDate fecha);
}