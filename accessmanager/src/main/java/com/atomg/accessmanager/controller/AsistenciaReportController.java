package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.service.AsistenciaService;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader; // Importación nueva necesaria
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin(origins = "*")
public class AsistenciaReportController {

    @Autowired
    private AsistenciaService asistenciaService;

    @GetMapping("/asistencia")
    public ResponseEntity<byte[]> descargarReporteAsistencia(
            @RequestParam String legajo,
            @RequestParam int anio,
            @RequestParam int mes,
            @RequestParam(required = false, defaultValue = "Empleado") String nombreEmpleado) {

        try {
            // 1. OBTENEMOS LA DATA REAL DE LA BASE DE DATOS
            List<ReporteAsistencia> listadoReal = asistenciaService.obtenerReporteMensual(legajo, anio, mes);

            // 2. FORMATEAMOS LA DATA PARA LOS FIELDS
            List<Map<String, String>> filasReporte = new ArrayList<>();
            DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("hh:mm a");
            DateTimeFormatter fechaFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            for (ReporteAsistencia registro : listadoReal) {
                Map<String, String> fila = new HashMap<>();
                fila.put("fecha", registro.getFecha() != null ? registro.getFecha().format(fechaFormatter) : "");
                fila.put("entrada", registro.getEntrada() != null ? registro.getEntrada().format(horaFormatter) : "--:--");
                fila.put("salida", registro.getSalida() != null ? registro.getSalida().format(horaFormatter) : "--:--");
                fila.put("horasTrabajadas", String.valueOf(registro.getHorasTrabajadas()) + " hs");
                fila.put("horasExtras", String.valueOf(registro.getHorasExtras()) + " hs");
                fila.put("estado", registro.getObservaciones() != null ? registro.getObservaciones() : "Normal");
                filasReporte.add(fila);
            }

            // =========================================================================
// 3. CARGA DIRECTA DEL NUEVO BINARIO COMPILADO MODERNIZADO (.jasper)
// =========================================================================
            ClassPathResource pdfResource = new ClassPathResource("reports/reporte_asistencia.jasper");
            if (!pdfResource.exists()) {
                throw new RuntimeException("No se encontró el archivo 'reporte_asistencia.jasper' en src/main/resources/reports/");
            }
            InputStream inputStream = pdfResource.getInputStream();

            // 4. MAPEO DE LOS PARÁMETROS DEL ENCABEZADO
            String[] nombresMeses = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
            String mesTexto = (mes >= 1 && mes <= 12) ? nombresMeses[mes] : String.valueOf(mes);
            String periodo = mesTexto + " " + anio;

            Map<String, Object> parametros = new HashMap<>();
            parametros.put("EMPLEADO_NOMBRE", nombreEmpleado);
            parametros.put("SUCURSAL", "Planta Central");
            parametros.put("SECCION", "Operaciones");
            parametros.put("PERIODO", periodo);

            // 5. INYECTAMOS LA DATA EN EL BINARIO DIRECTO SIN COMPILAR EN CALIENTE
// =========================================================================
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filasReporte);
            JasperPrint jasperPrint = JasperFillManager.fillReport(inputStream, parametros, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);

            // =========================================================================
// 6. HEADERS PARA VISTA PREVIA NATIVA (INLINE) EN VEZ DE DESCARGA FORZADA
// =========================================================================
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
// Cambiamos 'attachment' por 'inline' para que Chrome abra el visor
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"reporte_asistencia_" + legajo + ".pdf\"");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}