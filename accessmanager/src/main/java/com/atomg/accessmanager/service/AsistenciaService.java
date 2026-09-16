package com.atomg.accessmanager.service;

import com.atomg.accessmanager.dto.ResumenSectorDTO;
import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Licencia;
import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.repository.LicenciaRepository;
import com.atomg.accessmanager.repository.ReporteAsistenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AsistenciaService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private ReporteAsistenciaRepository reporteAsistenciaRepository;

    // ── MÓDULO 2: Licencias y Vacaciones ──────────────────────────────────────
    @Autowired
    private LicenciaRepository licenciaRepository;

    // =========================================================================
    // PROCESAMIENTO DE FICHADAS (guardado en BD)
    // =========================================================================

    /**
     * Procesa las fichadas crudas del dia y calcula las horas para guardarlas en la BD.
     * Si un empleado esta ausente pero tiene licencia activa ese dia,
     * el campo observaciones se reemplaza con el tipo de licencia (ej. "Vacaciones").
     */
    public void procesarYGuardarFichadas(LocalDate fecha, List<Fichada> fichadasCrudas) {
        fichadaRepository.saveAll(fichadasCrudas);

        List<Empleado> empleados = empleadoRepository.findAll();

        Map<String, List<Fichada>> fichadasPorEmpleado = fichadasCrudas.stream()
                .collect(Collectors.groupingBy(Fichada::getLegajoReloj));

        for (Empleado emp : empleados) {
            List<Fichada> fichadasEmp = fichadasPorEmpleado
                    .getOrDefault(emp.getLegajoReloj(), new ArrayList<>());

            ReporteAsistencia reporte = new ReporteAsistencia();
            reporte.setEmpleado(emp);
            reporte.setFecha(fecha);

            if (fichadasEmp.isEmpty()) {
                // Sin fichada: verificar si tiene licencia activa ese dia
                String obs = resolverObservacionAusencia(emp.getId(), fecha, "Ausente");
                reporte.setObservaciones(obs);
                reporteAsistenciaRepository.save(reporte);
                continue;
            }

            fichadasEmp.sort(Comparator.comparing(Fichada::getFechaHora));

            LocalDateTime entrada = fichadasEmp.get(0).getFechaHora();
            reporte.setEntrada(entrada);

            if (fichadasEmp.size() == 1) {
                reporte.setObservaciones("Falta Fichada de Salida");
                reporteAsistenciaRepository.save(reporte);
                continue;
            }

            LocalDateTime salida = fichadasEmp.get(fichadasEmp.size() - 1).getFechaHora();
            reporte.setSalida(salida);

            Duration duracion = Duration.between(entrada, salida);
            double horasTrabajadas = duracion.toMinutes() / 60.0;
            reporte.setHorasTrabajadas(Math.round(horasTrabajadas * 100.0) / 100.0);

            double jornadaBase = emp.getHorasJornadaBase() != null
                    ? emp.getHorasJornadaBase().doubleValue() : 8.0;

            if (horasTrabajadas > jornadaBase) {
                double extras = horasTrabajadas - Math.round(jornadaBase);
                reporte.setHorasExtras(Math.round(extras * 100.0) / 100.0);
                reporte.setObservaciones("Jornada Completa + Extras");
            } else {
                reporte.setHorasExtras(0.0);
                reporte.setObservaciones("Jornada Completa");
            }

            reporteAsistenciaRepository.save(reporte);
        }
    }

    // =========================================================================
    // CONSULTAS DE REPORTE
    // =========================================================================

    /**
     * Recupera el reporte mensual individual de un empleado (fichadas dia a dia).
     * Enriquece las filas con "Ausente" → tipo de licencia si corresponde.
     */
    public List<ReporteAsistencia> obtenerReporteMensual(String legajo, int anio, int mes) {
        LocalDate inicio = LocalDate.of(anio, mes, 1);
        LocalDate fin    = inicio.plusMonths(1).minusDays(1);
        List<ReporteAsistencia> reportes = reporteAsistenciaRepository
                .buscarReporteIndividual(legajo, inicio, fin);

        // Enriquecer filas "Ausente" con el tipo de licencia si hay una vigente
        reportes.forEach(r -> {
            if ("Ausente".equalsIgnoreCase(r.getObservaciones())
                    && r.getEmpleado() != null) {
                String obs = resolverObservacionAusencia(
                        r.getEmpleado().getId(), r.getFecha(), "Ausente");
                r.setObservaciones(obs);
            }
        });

        return reportes;
    }

    /**
     * Recupera el reporte masivo detallado filtrado por empresa + sucursal + sector + periodo.
     * Tambien enriquece los registros "Ausente" con el tipo de licencia.
     */
    public List<ReporteAsistencia> obtenerReporteMensualMasivo(
            Long empresaId, Long sucursalId, Long sectorId, int anio, int mes) {
        LocalDate fechaInicio = LocalDate.of(anio, mes, 1);
        LocalDate fechaFin    = fechaInicio.withDayOfMonth(fechaInicio.lengthOfMonth());
        List<ReporteAsistencia> reportes = reporteAsistenciaRepository
                .buscarReportesMasivos(empresaId, sucursalId, sectorId, fechaInicio, fechaFin);

        reportes.forEach(r -> {
            if ("Ausente".equalsIgnoreCase(r.getObservaciones())
                    && r.getEmpleado() != null) {
                String obs = resolverObservacionAusencia(
                        r.getEmpleado().getId(), r.getFecha(), "Ausente");
                r.setObservaciones(obs);
            }
        });

        return reportes;
    }

    /**
     * Devuelve el resumen agregado de asistencia para todos los empleados de un sector.
     */
    public List<ResumenSectorDTO> obtenerResumenSector(Long sectorId, int anio, int mes) {
        LocalDate fechaInicio = LocalDate.of(anio, mes, 1);
        LocalDate fechaFin    = fechaInicio.withDayOfMonth(fechaInicio.lengthOfMonth());
        return reporteAsistenciaRepository.obtenerResumenPorSector(sectorId, fechaInicio, fechaFin);
    }

    // =========================================================================
    // HELPERS PRIVADOS
    // =========================================================================

    /**
     * Verifica si el empleado tiene una licencia activa en la fecha dada.
     * Si la tiene, retorna el tipo de licencia como texto (ej. "Vacaciones").
     * Si no, retorna el valor por defecto recibido (ej. "Ausente").
     *
     * @param empleadoId    ID del empleado
     * @param fecha         Fecha a verificar
     * @param defaultValue  Valor a retornar si no hay licencia
     */
    private String resolverObservacionAusencia(Long empleadoId, LocalDate fecha, String defaultValue) {
        if (empleadoId == null || fecha == null) return defaultValue;
        Optional<Licencia> licencia = licenciaRepository
                .findLicenciaActivaEnFecha(empleadoId, fecha);
        return licencia.map(Licencia::getTipoLicencia).orElse(defaultValue);
    }
}