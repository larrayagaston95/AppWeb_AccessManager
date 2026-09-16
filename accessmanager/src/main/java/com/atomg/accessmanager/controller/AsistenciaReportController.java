package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.service.AsistenciaService;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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

    /** Nombres de meses en castellano indexados por número (1-12). */
    private static final String[] NOMBRES_MESES = {
            "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private static final DateTimeFormatter HORA_FMT  = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter FECHA_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // =========================================================================
    // HELPERS PRIVADOS
    // =========================================================================

    /** Devuelve "Mes AAAA" para los parámetros de encabezado de Jasper. */
    private String buildPeriodo(int mes, int anio) {
        String mesTexto = (mes >= 1 && mes <= 12) ? NOMBRES_MESES[mes] : String.valueOf(mes);
        return mesTexto + " " + anio;
    }

    /**
     * Carga el InputStream del logo institucional desde el classpath.
     * La imagen debe estar en src/main/resources/images/logo.png
     * Si no existe, devuelve null (Jasper ignora el campo).
     */
    private BufferedImage cargarLogo() {
        try {
            ClassPathResource logoResource = new ClassPathResource("reports/img/logo.png");
            if (logoResource.exists() && logoResource.contentLength() > 0) {
                try (InputStream is = logoResource.getInputStream()) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️  Logo no encontrado o vacío en classpath:reports/img/logo.png — el reporte se genera sin imagen.");
        }
        return null;
    }

    /**
     * Convierte un registro de ReporteAsistencia en un mapa plano compatible
     * con los fields definidos en el .jrxml.
     */
    private Map<String, String> toFilaJasper(ReporteAsistencia r) {
        Map<String, String> fila = new HashMap<>();

        String legajoEmp  = r.getEmpleado() != null ? r.getEmpleado().getLegajoReloj() : "";
        String nombreComp = r.getEmpleado() != null
                ? r.getEmpleado().getNombre() + " " + r.getEmpleado().getApellido()
                : "";

        fila.put("legajo",          legajoEmp);
        fila.put("LEGAJO",          legajoEmp);
        fila.put("EMPLEADO_NOMBRE", nombreComp);
        fila.put("empleadoNombre",  nombreComp);
        fila.put("fecha",           r.getFecha()   != null ? r.getFecha().format(FECHA_FMT)  : "");
        fila.put("entrada",         r.getEntrada() != null ? r.getEntrada().format(HORA_FMT) : "--:--");
        fila.put("salida",          r.getSalida()  != null ? r.getSalida().format(HORA_FMT)  : "--:--");
        fila.put("horasTrabajadas", r.getHorasTrabajadas() + " hs");
        fila.put("horasExtras",     r.getHorasExtras()     + " hs");
        fila.put("estado",          r.getObservaciones()   != null ? r.getObservaciones() : "Normal");
        return fila;
    }

    // =========================================================================
    // ENDPOINT 1 — PDF INDIVIDUAL (por legajo + período)
    // GET /api/reportes/asistencia?legajo=&anio=&mes=&nombreEmpleado=
    // =========================================================================
    @GetMapping("/asistencia")
    public ResponseEntity<byte[]> descargarReporteAsistencia(
            @RequestParam String legajo,
            @RequestParam int    anio,
            @RequestParam int    mes,
            @RequestParam(required = false, defaultValue = "Empleado") String nombreEmpleado) {

        try {
            List<ReporteAsistencia> listadoReal = asistenciaService.obtenerReporteMensual(legajo, anio, mes);

            if (listadoReal.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            String nombreEmpresaReal = listadoReal.get(0).getEmpleado().getSector().getSucursal().getEmpresa().getNombre();
            String nombreSucursalReal = listadoReal.get(0).getEmpleado().getSector().getSucursal().getNombre();
            String nombreSectorReal = listadoReal.get(0).getEmpleado().getSector().getNombre();

            // Formateamos filas para Jasper
            List<Map<String, String>> filasReporte = new ArrayList<>();
            for (ReporteAsistencia r : listadoReal) {
                Map<String, String> fila = new HashMap<>();
                fila.put("fecha",           r.getFecha()   != null ? r.getFecha().format(FECHA_FMT)  : "");
                fila.put("entrada",         r.getEntrada() != null ? r.getEntrada().format(HORA_FMT) : "--:--");
                fila.put("salida",          r.getSalida()  != null ? r.getSalida().format(HORA_FMT)  : "--:--");
                fila.put("horasTrabajadas", r.getHorasTrabajadas() + " hs");
                fila.put("horasExtras",     r.getHorasExtras()     + " hs");
                fila.put("estado",          r.getObservaciones()   != null ? r.getObservaciones() : "Normal");
                filasReporte.add(fila);
            }

            // Carga del template fuente (.jrxml)
            ClassPathResource jrxmlResource = new ClassPathResource("reports/reporte_asistencia.jrxml");
            if (!jrxmlResource.exists()) {
                throw new RuntimeException("No se encontró 'reporte_asistencia.jrxml'");
            }

            Map<String, Object> parametros = new HashMap<>();
            parametros.put("EMPRESA_NOMBRE", nombreEmpresaReal);
            parametros.put("EMPLEADO_NOMBRE", nombreEmpleado);
            parametros.put("SUCURSAL", nombreSucursalReal);
            parametros.put("SECCION",  nombreSectorReal);
            parametros.put("PERIODO",  buildPeriodo(mes, anio));

            // Logo (null-safe: Jasper lo omite si es null)
            BufferedImage logoImage = cargarLogo();
            if (logoImage != null) {
                parametros.put("LOGO_PATH", logoImage);
            }

            JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(filasReporte, false);
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlResource.getInputStream());
            JasperPrint print = JasperFillManager.fillReport(jasperReport, parametros, ds);
            byte[] pdfBytes   = JasperExportManager.exportReportToPdf(print);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"reporte_asistencia_" + legajo + ".pdf\"");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // ENDPOINT 2 — PDF MASIVO (paginado por empleado dentro del sector)
    // GET /api/reportes/asistencia-masiva?empresaId=&sucursalId=&sectorId=&anio=&mes=
    // =========================================================================
    @GetMapping("/asistencia-masiva")
    public ResponseEntity<byte[]> descargarReporteAsistenciaMasiva(
            HttpServletRequest request,
            @RequestParam Long sucursalId,
            @RequestParam Long sectorId,
            @RequestParam int  anio,
            @RequestParam int  mes) {

        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            List<ReporteAsistencia> listadoReal = asistenciaService.obtenerReporteMensualMasivo(
                    empresaId, sucursalId, sectorId, anio, mes);

            if (listadoReal.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Textos reales para los parámetros de encabezado (navegamos JPA)
            String nombreEmpresaReal   = listadoReal.get(0).getEmpleado().getSector().getSucursal().getEmpresa().getNombre();
            String nombreSucursalReal  = listadoReal.get(0).getEmpleado().getSector().getSucursal().getNombre();
            String nombreSectorReal    = listadoReal.get(0).getEmpleado().getSector().getNombre();

            // Formateamos TODAS las filas (la agrupación por empleado la hace Jasper)
            List<Map<String, String>> filasReporte = new ArrayList<>();
            for (ReporteAsistencia r : listadoReal) {
                filasReporte.add(toFilaJasper(r));
            }

            // Carga del template fuente masivo (.jrxml)
            ClassPathResource jrxmlResource = new ClassPathResource("reports/reporte_asistencia_masivo.jrxml");
            if (!jrxmlResource.exists()) {
                throw new RuntimeException("No se encontró 'reporte_asistencia_masivo.jrxml'");
            }

            // Parámetros globales (encabezado del reporte)
            Map<String, Object> parametros = new HashMap<>();
            parametros.put("EMPRESA_NOMBRE", nombreEmpresaReal);
            parametros.put("SUCURSAL", nombreSucursalReal);
            parametros.put("SECCION",  nombreSectorReal);
            parametros.put("PERIODO",  buildPeriodo(mes, anio));

            // Logo institucional desde classpath (null-safe)
            BufferedImage logoImage = cargarLogo();
            if (logoImage != null) {
                parametros.put("LOGO_PATH", logoImage);
            }

            JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(filasReporte, false);
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlResource.getInputStream());
            JasperPrint print = JasperFillManager.fillReport(jasperReport, parametros, ds);
            byte[] pdfBytes   = JasperExportManager.exportReportToPdf(print);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"reporte_masivo_" + nombreSectorReal.replace(" ", "_") + ".pdf\"");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
