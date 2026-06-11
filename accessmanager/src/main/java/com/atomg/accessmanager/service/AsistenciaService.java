package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
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
import java.util.stream.Collectors;

@Service
public class AsistenciaService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private ReporteAsistenciaRepository reporteAsistenciaRepository;

    /**
     * Procesa las fichadas crudas del día y calcula las horas para guardarlas en la BD.
     */
    public void procesarYGuardarFichadas(LocalDate fecha, List<Fichada> fichadasCrudas) {
        // 1. Guardamos los registros crudos en la BD por las dudas (Auditoría)
        fichadaRepository.saveAll(fichadasCrudas);

        // 2. Traemos todos los empleados registrados para saber sus jornadas base
        List<Empleado> empleados = empleadoRepository.findAll();

        // Agrupamos las fichadas que llegaron del reloj por número de legajo
        Map<String, List<Fichada>> fichadasPorEmpleado = fichadasCrudas.stream()
                .collect(Collectors.groupingBy(Fichada::getLegajoReloj));

        for (Empleado emp : empleados) {
            List<Fichada> fichadasEmp = fichadasPorEmpleado.getOrDefault(emp.getLegajoReloj(), new ArrayList<>());

            ReporteAsistencia reporte = new ReporteAsistencia();
            reporte.setEmpleado(emp);
            reporte.setFecha(fecha);

            if (fichadasEmp.isEmpty()) {
                reporte.setObservaciones("Ausente");
                reporteAsistenciaRepository.save(reporte);
                continue;
            }

            // Ordenamos los fichajes por hora de menor a mayor
            fichadasEmp.sort(Comparator.comparing(Fichada::getFechaHora));

            // Primera del día = Entrada
            LocalDateTime entrada = fichadasEmp.get(0).getFechaHora();
            reporte.setEntrada(entrada);

            // Si tiene una sola fichada, le falta la contraparte
            if (fichadasEmp.size() == 1) {
                reporte.setObservaciones("Falta Fichada de Salida");
                reporteAsistenciaRepository.save(reporte);
                continue;
            }

            // Última del día = Salida
            LocalDateTime salida = fichadasEmp.get(fichadasEmp.size() - 1).getFechaHora();
            reporte.setSalida(salida);

            // --- MÁQUINA DE CÁLCULO ---
            Duration duracion = Duration.between(entrada, salida);
            double horasTrabajadas = duracion.toMinutes() / 60.0;
            reporte.setHorasTrabajadas(Math.round(horasTrabajadas * 100.0) / 100.0);

            double jornadaBase = emp.getHorasJornadaBase();
            if (horasTrabajadas > jornadaBase) {
                double extras = horasTrabajadas - Math.round(jornadaBase);
                reporte.setHorasExtras(Math.round(extras * 100.0) / 100.0);
                reporte.setObservaciones("Jornada Completa + Extras");
            } else {
                reporte.setHorasExtras(0.0);
                reporte.setObservaciones("Jornada Completa");
            }

            // Guardamos el reporte final procesado en MySQL
            reporteAsistenciaRepository.save(reporte);
        }
    }

    /**
     * Recupera el reporte mensual de un empleado desde la base de datos
     */
    public List<ReporteAsistencia> obtenerReporteMensual(String legajo, int anio, int mes) {
        LocalDate inicio = LocalDate.of(anio, mes, 1);
        LocalDate fin = inicio.plusMonths(1).minusDays(1);
        return reporteAsistenciaRepository.findByEmpleadoLegajoRelojAndFechaBetweenOrderByFechaAsc(legajo, inicio, fin);
    }
}