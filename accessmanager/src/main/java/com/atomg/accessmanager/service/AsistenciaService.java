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

    /**
     * Guarda la fichada (Historial crudo) e intenta emparejarla dinámicamente con un
     * ReporteAsistencia del mismo día EXACTO, calculando entrada, salida y horas trabajadas.
     */
    public Fichada guardarFichada(Fichada fichada) {
        // 1. Guardar registro crudo siempre
        Fichada fichadaGuardada = fichadaRepository.save(fichada);

        try {
            // 2. Buscar al empleado por legajo y empresa
            Optional<Empleado> empOpt = Optional.empty();
            if (fichada.getIdEmpresa() != null && fichada.getLegajoReloj() != null) {
                empOpt = empleadoRepository.findByLegajoRelojAndEmpresaId(fichada.getLegajoReloj(), fichada.getIdEmpresa());
            } else if (fichada.getLegajoReloj() != null) {
                empOpt = empleadoRepository.findByLegajoReloj(fichada.getLegajoReloj());
            }

            if (empOpt.isPresent()) {
                Empleado emp = empOpt.get();

                // 3. Extraer el día EXACTO de la fechaHora de la fichada
                LocalDate fechaDia = fichada.getFechaHora().toLocalDate();
                System.out.println("[DEBUG guardarFichada] Emparejando para empleado ID=" + emp.getId()
                        + " | legajo=" + emp.getLegajoReloj()
                        + " | fechaHora fichada=" + fichada.getFechaHora()
                        + " | fechaDia (LocalDate)=" + fechaDia);

                // 4. Buscar reporte EXCLUSIVO de ese día (query JPQL explícita con WHERE fecha = :fecha)
                Optional<ReporteAsistencia> reporteOpt = reporteAsistenciaRepository
                        .buscarPorEmpleadoYFecha(emp.getId(), fechaDia);

                ReporteAsistencia reporte;
                if (reporteOpt.isEmpty()) {
                    // Primera fichada del día → crear reporte nuevo
                    System.out.println("[DEBUG guardarFichada] No existe reporte para fecha=" + fechaDia + ". Creando nuevo (ENTRADA).");
                    reporte = new ReporteAsistencia();
                    reporte.setEmpleado(emp);
                    reporte.setFecha(fechaDia);
                    reporte.setEntrada(fichada.getFechaHora());
                    reporte.setHorasTrabajadas(0.0);
                    reporte.setHorasExtras(0.0);
                    reporte.setObservaciones("Falta Fichada de Salida");
                } else {
                    // Ya existe el reporte para ese día → actualizar salida y calcular horas
                    reporte = reporteOpt.get();
                    System.out.println("[DEBUG guardarFichada] Reporte existente encontrado ID=" + reporte.getId()
                            + " | fecha=" + reporte.getFecha()
                            + " | entrada actual=" + reporte.getEntrada()
                            + " → Registrando SALIDA=" + fichada.getFechaHora());

                    reporte.setSalida(fichada.getFechaHora());

                    if (reporte.getEntrada() != null) {
                        Duration duracion = Duration.between(reporte.getEntrada(), reporte.getSalida());
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
                        System.out.println("[DEBUG guardarFichada] Horas calculadas: " + reporte.getHorasTrabajadas()
                                + " hs | Extras: " + reporte.getHorasExtras()
                                + " hs | Obs: " + reporte.getObservaciones());
                    }
                }

                reporteAsistenciaRepository.save(reporte);
                System.out.println("[DEBUG guardarFichada] ReporteAsistencia guardado OK para fecha=" + fechaDia);
            } else {
                System.out.println("[DEBUG guardarFichada] Empleado NO encontrado para legajo="
                        + fichada.getLegajoReloj() + " | empresaId=" + fichada.getIdEmpresa()
                        + ". No se genera ReporteAsistencia.");
            }
        } catch (Exception e) {
            System.err.println("[ERROR guardarFichada] Fallo emparejando ReporteAsistencia: " + e.getMessage());
            e.printStackTrace();
        }

        return fichadaGuardada;
    }

    // =========================================================================
    // CONSULTAS DE REPORTE
    // =========================================================================

    /**
     * Recupera el reporte mensual individual de un empleado (fichadas dia a dia).
     * Enriquece las filas con "Ausente" → tipo de licencia si corresponde.
     */
    /**
     * Recupera el reporte mensual individual de un empleado (fichadas dia a dia).
     * Recorrido del dato:
     * 1. El Controlador extrae el ID de la Empresa y los parametros de fecha y legajo.
     * 2. El Servicio procesa estas variables e invoca al Repositorio.
     * 3. El Repositorio aisla la informacion (Seguridad Multi-Tenant).
     * 4. Se enriquecen en memoria las ausencias procesando posibles licencias.
     */
    public List<ReporteAsistencia> obtenerReporteMensual(String legajoDelEmpleado, Long identificadorDeLaEmpresa, int anioRequerido, int mesRequerido) {
        try {
            System.out.println("=== [DEBUG AsistenciaService] obtenerReporteMensual ===");
            System.out.println("[DEBUG] Parámetros recibidos -> legajo: '" + legajoDelEmpleado
                    + "' | empresaId: " + identificadorDeLaEmpresa
                    + " | anio: " + anioRequerido
                    + " | mes: " + mesRequerido);

            System.out.println("[DEBUG] Rango de fechas calculado -> anio: " + anioRequerido + " | mes: " + mesRequerido);
            
            // Se realiza la busqueda estrictamente combinando legajo y empresa (Multi-Tenant) extrayendo el año y mes
            List<ReporteAsistencia> listaDeReportes = reporteAsistenciaRepository
                    .buscarReporteIndividual(legajoDelEmpleado, identificadorDeLaEmpresa, anioRequerido, mesRequerido);

            System.out.println("[DEBUG] Cantidad de ReporteAsistencia recuperados de la BD: " + listaDeReportes.size());

            // Log detallado de cada registro recuperado
            for (int i = 0; i < listaDeReportes.size(); i++) {
                ReporteAsistencia r = listaDeReportes.get(i);
                System.out.println("[DEBUG] Reporte[" + i + "] -> fecha=" + r.getFecha()
                        + " | entrada=" + r.getEntrada()
                        + " | salida=" + r.getSalida()
                        + " | horasTrabajadas=" + r.getHorasTrabajadas()
                        + " | obs=" + r.getObservaciones());
            }

            // Enriquecer filas "Ausente" cruzando datos con el repositorio de licencias
            listaDeReportes.forEach(reporteIterado -> {
                try {
                    if ("Ausente".equalsIgnoreCase(reporteIterado.getObservaciones()) && reporteIterado.getEmpleado() != null) {
                        String observacionPorLicencia = resolverObservacionAusencia(
                                reporteIterado.getEmpleado().getId(), reporteIterado.getFecha(), "Ausente");
                        reporteIterado.setObservaciones(observacionPorLicencia);
                    }
                } catch (Exception excepcionIteracion) {
                    System.err.println("Error en AsistenciaService.java -> obtenerReporteMensual: Fallo procesando licencia - " + excepcionIteracion.getMessage());
                }
            });

            System.out.println("[DEBUG] ======================================================");
            return listaDeReportes;
        } catch (Exception excepcionConsulta) {
            System.err.println("Error en AsistenciaService.java -> obtenerReporteMensual: Fallo general al buscar reportes - " + excepcionConsulta.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Recupera el reporte masivo detallado filtrado por empresa + sucursal + sector + periodo.
     * Tambien enriquece los registros "Ausente" con el tipo de licencia.
     */
    public List<ReporteAsistencia> obtenerReporteMensualMasivo(
            Long empresaId, Long sucursalId, Long sectorId, int anio, int mes) {
        List<ReporteAsistencia> reportes = reporteAsistenciaRepository
                .buscarReportesMasivos(empresaId, sucursalId, sectorId, anio, mes);

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
        return reporteAsistenciaRepository.obtenerResumenPorSector(sectorId, anio, mes);
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
