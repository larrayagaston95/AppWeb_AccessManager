package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.AsistenciaDiaria;
import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Turno;
import com.atomg.accessmanager.repository.AsistenciaDiariaRepository;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.repository.TurnoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MotorCalculoService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private AsistenciaDiariaRepository asistenciaDiariaRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    public void procesarDiaEmpleado(String legajo, LocalDate fecha) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findByLegajoReloj(legajo);
        if (empleadoOpt.isEmpty()) return;
        
        Empleado empleado = empleadoOpt.get();
        Optional<Empresa> empresaOpt = empresaRepository.findById(empleado.getEmpresaId());
        if (empresaOpt.isEmpty()) return;
        
        Empresa empresa = empresaOpt.get();
        String modelo = empresa.getModeloAsistencia();
        
        if (modelo == null || modelo.equals("AUTOMATICO")) {
            calcularBolsaAutomatica(empleado, fecha);
        } else if (modelo.equals("TURNOS_FIJOS")) {
            calcularPorTurnoFijo(empleado, fecha);
        }
    }

    private void calcularBolsaAutomatica(Empleado empleado, LocalDate fecha) {
        LocalDateTime inicio = fecha.atStartOfDay();
        LocalDateTime fin = fecha.atTime(23, 59, 59);
        
        List<Fichada> fichadas = fichadaRepository.findByLegajoRelojAndFechaHoraBetweenOrderByFechaHoraAsc(
            empleado.getLegajoReloj(), inicio, fin);
            
        long minutosTotales = 0;
        
        for (int i = 0; i < fichadas.size() - 1; i += 2) {
            LocalDateTime in = fichadas.get(i).getFechaHora();
            LocalDateTime out = fichadas.get(i + 1).getFechaHora();
            minutosTotales += Duration.between(in, out).toMinutes();
        }
        
        double horasTrabajadas = minutosTotales / 60.0;
        horasTrabajadas = Math.round(horasTrabajadas * 100.0) / 100.0;
        
        Integer jornadaBaseInt = empleado.getHorasJornadaBase();
        Double jornadaBase = (jornadaBaseInt != null) ? (double) jornadaBaseInt : 9.0;
        
        double horasNormales = Math.min(horasTrabajadas, jornadaBase);
        double horasExtras = Math.max(0.0, horasTrabajadas - jornadaBase);
        
        Optional<AsistenciaDiaria> asisOpt = asistenciaDiariaRepository.findByLegajoRelojAndFecha(
            empleado.getLegajoReloj(), fecha);
            
        AsistenciaDiaria asistencia;
        if (asisOpt.isPresent()) {
            asistencia = asisOpt.get();
        } else {
            asistencia = new AsistenciaDiaria();
            asistencia.setFecha(fecha);
            asistencia.setLegajoReloj(empleado.getLegajoReloj());
            asistencia.setIdEmpresa(empleado.getEmpresaId());
        }
        
        asistencia.setHorasNormales(horasNormales);
        asistencia.setHorasExtras(horasExtras);
        
        if (horasTrabajadas >= jornadaBase) {
            asistencia.setEstado("COMPLETO");
        } else {
            asistencia.setEstado("DEFICIT");
        }
        
        asistenciaDiariaRepository.save(asistencia);
        System.out.println("[MOTOR] Cálculo Automático OK: " + empleado.getLegajoReloj() + " -> " + horasTrabajadas + " hs");
    }

    private void calcularPorTurnoFijo(Empleado empleado, LocalDate fecha) {
        if (empleado.getTurnoId() == null) {
            calcularBolsaAutomatica(empleado, fecha);
            return;
        }

        Optional<Turno> turnoOpt = turnoRepository.findById(empleado.getTurnoId());
        if (turnoOpt.isEmpty()) {
            calcularBolsaAutomatica(empleado, fecha);
            return;
        }

        Turno turno = turnoOpt.get();
        LocalDateTime inicio = fecha.atStartOfDay();
        LocalDateTime fin = fecha.atTime(23, 59, 59);

        List<Fichada> fichadas = fichadaRepository.findByLegajoRelojAndFechaHoraBetweenOrderByFechaHoraAsc(
                empleado.getLegajoReloj(), inicio, fin);

        Optional<AsistenciaDiaria> asisOpt = asistenciaDiariaRepository.findByLegajoRelojAndFecha(
                empleado.getLegajoReloj(), fecha);
        
        AsistenciaDiaria asistencia = asisOpt.orElseGet(() -> {
            AsistenciaDiaria a = new AsistenciaDiaria();
            a.setFecha(fecha);
            a.setLegajoReloj(empleado.getLegajoReloj());
            a.setIdEmpresa(empleado.getEmpresaId());
            return a;
        });

        if (fichadas.isEmpty()) {
            asistencia.setEstado("AUSENTE");
            asistencia.setHorasNormales(0.0);
            asistencia.setHorasExtras(0.0);
            asistencia.setMinutosTarde(0);
            asistenciaDiariaRepository.save(asistencia);
            System.out.println("[MOTOR] Turno Fijo: " + empleado.getLegajoReloj() + " -> AUSENTE");
            return;
        }

        // Calcula minutos tarde
        LocalDateTime primeraFichada = fichadas.get(0).getFechaHora();
        LocalDateTime horarioEntradaTeorico = fecha.atTime(turno.getHoraEntrada());
        int minutosTarde = 0;
        
        System.out.println("[DEBUG-TARDE] Primera Fichada Real: " + primeraFichada);
        System.out.println("[DEBUG-TARDE] Hora Entrada Teórica (Turno): " + horarioEntradaTeorico);
        System.out.println("[DEBUG-TARDE] Tolerancia permitida: " + turno.getToleranciaMinutos() + " min");
        System.out.println("[DEBUG-TARDE] Límite con tolerancia: " + horarioEntradaTeorico.plusMinutes(turno.getToleranciaMinutos()));
        
        if (primeraFichada.isAfter(horarioEntradaTeorico.plusMinutes(turno.getToleranciaMinutos()))) {
            minutosTarde = (int) Duration.between(horarioEntradaTeorico, primeraFichada).toMinutes();
        }

        // Calcula horas trabajadas
        long minutosTotales = 0;
        for (int i = 0; i < fichadas.size() - 1; i += 2) {
            LocalDateTime in = fichadas.get(i).getFechaHora();
            LocalDateTime out = fichadas.get(i + 1).getFechaHora();
            minutosTotales += Duration.between(in, out).toMinutes();
        }

        double horasTrabajadas = minutosTotales / 60.0;
        horasTrabajadas = Math.round(horasTrabajadas * 100.0) / 100.0;

        double duracionTurno = Duration.between(turno.getHoraEntrada(), turno.getHoraSalida()).toMinutes() / 60.0;
        duracionTurno = Math.round(duracionTurno * 100.0) / 100.0;
        if(duracionTurno <= 0) {
            duracionTurno = 24.0 + duracionTurno; // Para turnos que pasan de medianoche (solo un fix rapido si fuera necesario)
        }

        double horasNormales = Math.min(horasTrabajadas, duracionTurno);
        double horasExtras = Math.max(0.0, horasTrabajadas - duracionTurno);

        asistencia.setHorasNormales(horasNormales);
        asistencia.setHorasExtras(horasExtras);
        asistencia.setMinutosTarde(minutosTarde);
        
        if (horasTrabajadas >= duracionTurno && minutosTarde == 0) {
            asistencia.setEstado("COMPLETO");
        } else {
            asistencia.setEstado("DEFICIT");
        }

        asistenciaDiariaRepository.save(asistencia);
        System.out.println("[MOTOR] Turno Fijo OK: " + empleado.getLegajoReloj() + " -> Tarde: " + minutosTarde + " min | Extras: " + horasExtras + " hs");
    }
}