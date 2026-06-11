/**
 * Genera el PDF corporativo de asistencia de forma programática.
 * @param {Object} empleado - Datos del operario seleccionado.
 * @param {Array} filasTabla - Array con los fichajes de la tabla del front.
 */
export function exportarAsistenciaPDF(empleado, filasTabla) {
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4" });

    // 1. Encabezado e Identidad (Arriba a la izquierda)
    doc.setFont("Helvetica", "bold");
    doc.setFontSize(14);
    doc.text("FLUXTECH ACCESSMANAGER", 14, 15);

    doc.setFontSize(9);
    doc.setFont("Helvetica", "normal");
    doc.text("EMPRESA: COOPERATIVA CENTRAL", 14, 20);

    // Fecha de emisión (Arriba a la derecha)
    doc.text("EMISIÓN: 11/06/2026", 150, 15);

    // Línea divisoria firme
    doc.setDrawColor(0, 0, 0);
    doc.setLineWidth(0.5);
    doc.line(14, 23, 196, 23);

    // 2. Ficha Técnica del Empleado (Datos arriba de la tabla)
    doc.setFont("Helvetica", "bold");
    doc.text(`EMPLEADO: ${empleado.nombre} (Legajo ${empleado.legajo})`, 14, 30);
    doc.text(`SUCURSAL: ${empleado.sucursal}`, 14, 35);
    doc.text(`SECCIÓN: ${empleado.seccion}`, 110, 30);
    doc.text(`PERÍODO: ${empleado.periodo}`, 110, 35);

    // 3. Título del Reporte
    doc.setFontSize(12);
    doc.text("Detalle de Asistencia Mensual", 14, 45);

    // 4. Estructura de la Tabla de Horarios (Idéntica a tu diseño de papel)
    const columnas = ["Fecha", "Entrada", "Salida", "Horas Trab.", "Horas Ext.", "Estado / Observación"];

    doc.autoTable({
        startY: 48,
        head: [columnas],
        body: filasTabla,
        theme: 'plain', // Fondo blanco limpio, estilo papel de oficina
        styles: {
            font: "Helvetica",
            fontSize: 9,
            cellPadding: 2.5,
            textColor: [0, 0, 0],
            lineColor: [0, 0, 0],
            lineWidth: 0.2
        },
        headStyles: {
            fontStyle: "bold",
            fillColor: [240, 240, 240], // Gris muy sutil para los títulos
        }
    });

    // 5. Descarga directa del archivo
    doc.save(`asistencia_${empleado.legajo}_${empleado.periodo}.pdf`);
}