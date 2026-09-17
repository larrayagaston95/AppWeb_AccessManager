package com.atomg.accessmanager.job;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.service.MotorCalculoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CalculoNocturnoJob {

    @Autowired
    private MotorCalculoService motorCalculoService;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    public void procesarCierreDiario() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        
        System.out.println("=========================================");
        System.out.println("[CRON] Iniciando cálculo de asistencia nocturno para la fecha: " + ayer);
        
        List<Empleado> empleados = empleadoRepository.findAll();
        
        for (Empleado empleado : empleados) {
            motorCalculoService.procesarDiaEmpleado(empleado.getLegajoReloj(), ayer);
        }
        
        System.out.println("[CRON] Cálculo nocturno finalizado. Empleados procesados: " + empleados.size());
        System.out.println("=========================================");
    }
}