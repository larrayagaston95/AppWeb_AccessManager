/**
 * Dibuja las filas de asistencia dentro del cuerpo de la tabla.
 * @param {Array} data - Lista de objetos de asistencia mensual devueltos por la API.
 */
export function renderTable(data) {
    const tbody = document.getElementById('tablaCuerpo');

    if (!data || data.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-muted py-4">
                    No hay registros de asistencia calculados para este período.
                </td>
            </tr>`;
        return;
    }

    let html = '';
    data.forEach(item => {
        // Formateo de fecha y horas al estándar local (es-AR)
        const fechaFmt = new Date(item.fecha + 'T00:00:00').toLocaleDateString('es-AR', {day: '2-digit', month: '2-digit', year: 'numeric'});
        const entradaFmt = item.entrada ? new Date(item.entrada).toLocaleTimeString('es-AR', {hour: '2-digit', minute:'2-digit'}) : '--:--';
        const salidaFmt = item.salida ? new Date(item.salida).toLocaleTimeString('es-AR', {hour: '2-digit', minute:'2-digit'}) : '--:--';

        // Lógica visual para destacar las observaciones que devuelve el motor del backend
        let badgeColor = 'bg-success';
        if (item.observaciones.includes('Falta')) badgeColor = 'bg-danger';
        if (item.observaciones.includes('Ausente')) badgeColor = 'bg-secondary';
        if (item.observaciones.includes('Extras')) badgeColor = 'bg-warning text-dark';

        html += `
            <tr>
                <td><strong>${fechaFmt}</strong></td>
                <td><i class="bi bi-box-arrow-in-right text-success me-2"></i>${entradaFmt}</td>
                <td><i class="bi bi-box-arrow-left text-danger me-2"></i>${salidaFmt}</td>
                <td class="text-center fw-bold">${item.horasTrabajadas.toFixed(2)} hs</td>
                <td class="text-center fw-bold text-turquoise">${item.horasExtras.toFixed(2)} hs</td>
                <td><span class="badge ${badgeColor}">${item.observaciones}</span></td>
            </tr>
        `;
    });

    tbody.innerHTML = html;
}