// ============================================================================
// tableRenderer.js
// Renderiza el contenido de la tabla de asistencia en dos modos:
//   - INDIVIDUAL: detalle día a día de un empleado
//   - SECTOR:     resumen consolidado (un renglón por empleado con totales)
// ============================================================================

const COL_INDIVIDUAL = `
    <tr>
        <th>Fecha</th>
        <th>Entrada</th>
        <th>Salida</th>
        <th class="text-center">Horas Trabajadas</th>
        <th class="text-center">Horas Extras</th>
        <th>Estado / Observación</th>
    </tr>`;

const COL_SECTOR = `
    <tr>
        <th>Legajo</th>
        <th>Apellido y Nombre</th>
        <th class="text-center">Días Trabajados</th>
        <th class="text-center">Total Horas</th>
        <th class="text-center">Total Extras</th>
    </tr>`;

/**
 * Intercambia las cabeceras de la tabla según el modo activo.
 * @param {'individual'|'sector'} modo
 */
function actualizarCabeceras(modo) {
    const thead = document.querySelector('#tablaCuerpo')?.closest('table')?.querySelector('thead');
    if (!thead) return;
    thead.innerHTML = modo === 'individual' ? COL_INDIVIDUAL : COL_SECTOR;
}

// ── MODO INDIVIDUAL ──────────────────────────────────────────────────────────

/**
 * Renderiza la tabla de fichajes diarios de un empleado.
 * @param {Array<Object>} data - Lista de registros de asistencia del backend.
 */
export function renderTableIndividual(data) {
    actualizarCabeceras('individual');
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

    tbody.innerHTML = data.map(item => {
        // Formateo de fecha y horas al estándar local (es-AR)
        const fechaFmt   = new Date(item.fecha + 'T00:00:00')
            .toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
        const entradaFmt = item.entrada
            ? new Date(item.entrada).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
            : '--:--';
        const salidaFmt  = item.salida
            ? new Date(item.salida).toLocaleTimeString('es-AR',  { hour: '2-digit', minute: '2-digit' })
            : '--:--';

        // Badge de color según observación
        const obs = item.observaciones ?? '';
        let badgeColor = 'bg-success';
        if (obs.includes('Falta'))   badgeColor = 'bg-danger';
        if (obs.includes('Ausente')) badgeColor = 'bg-secondary';
        if (obs.includes('Extras'))  badgeColor = 'bg-warning text-dark';

        return `
            <tr>
                <td><strong>${fechaFmt}</strong></td>
                <td><i class="bi bi-box-arrow-in-right text-success me-2"></i>${entradaFmt}</td>
                <td><i class="bi bi-box-arrow-left text-danger me-2"></i>${salidaFmt}</td>
                <td class="text-center fw-bold">${(item.horasTrabajadas ?? 0).toFixed(2)} hs</td>
                <td class="text-center fw-bold text-turquoise">${(item.horasExtras ?? 0).toFixed(2)} hs</td>
                <td><span class="badge ${badgeColor}">${obs}</span></td>
            </tr>`;
    }).join('');
}

/**
 * Nueva función solicitada para el endpoint /detalle
 * Limpia el tbody y formatea los datos con las nuevas claves
 */
export function renderizarTablaAsistencia(datos) {
    actualizarCabeceras('individual');
    const tbody = document.getElementById('tablaCuerpo');

    if (!datos || datos.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-muted py-4">
                    No hay registros para este período.
                </td>
            </tr>`;
        return;
    }

    tbody.innerHTML = datos.map(item => {
        const fechaFmt = item.fecha 
            ? new Date(item.fecha + 'T00:00:00').toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' })
            : '';
        const entradaFmt = item.hora_entrada 
            ? new Date(item.hora_entrada).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
            : '--:--';
        const salidaFmt = item.hora_salida 
            ? new Date(item.hora_salida).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
            : '--:--';

        const obs = item.estado ?? '';
        let badgeColor = 'bg-success';
        if (obs.includes('Falta'))   badgeColor = 'bg-danger';
        if (obs.includes('Ausente')) badgeColor = 'bg-secondary';
        if (obs.includes('Extras'))  badgeColor = 'bg-warning text-dark';

        return `
            <tr>
                <td><strong>${fechaFmt}</strong></td>
                <td><i class="bi bi-box-arrow-in-right text-success me-2"></i>${entradaFmt}</td>
                <td><i class="bi bi-box-arrow-left text-danger me-2"></i>${salidaFmt}</td>
                <td class="text-center fw-bold">${(item.horas_trabajadas ?? 0).toFixed(2)} hs</td>
                <td class="text-center fw-bold text-turquoise">${(item.horas_extras ?? 0).toFixed(2)} hs</td>
                <td><span class="badge ${badgeColor}">${obs}</span></td>
            </tr>`;
    }).join('');
}

// ── MODO SECTOR (RESUMEN CONSOLIDADO) ────────────────────────────────────────

/**
 * Renderiza la tabla de resumen del sector: un renglón por empleado con totales.
 * @param {Array<Object>} data - Lista de ResumenSectorDTO del backend.
 *   Cada item: { legajo, nombre, apellido, diasTrabajados, totalHoras, totalExtras }
 */
export function renderTableSector(data) {
    actualizarCabeceras('sector');
    const tbody = document.getElementById('tablaCuerpo');

    if (!data || data.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-muted py-4">
                    No hay registros de asistencia para el sector y período seleccionados.
                </td>
            </tr>`;
        return;
    }

    tbody.innerHTML = data.map(emp => {
        // Color de días trabajados según cantidad
        const diasClass = emp.diasTrabajados > 0 ? 'text-success' : 'text-muted';

        return `
            <tr>
                <td><span class="badge bg-secondary fw-bold">${emp.legajo}</span></td>
                <td class="fw-bold">${emp.apellido}, ${emp.nombre}</td>
                <td class="text-center">
                    <span class="${diasClass} fw-bold">${emp.diasTrabajados}</span>
                </td>
                <td class="text-center fw-bold">${emp.totalHoras.toFixed(2)} hs</td>
                <td class="text-center fw-bold text-turquoise">${emp.totalExtras.toFixed(2)} hs</td>
            </tr>`;
    }).join('');
}

// ── ALIAS RETROCOMPATIBLE ─────────────────────────────────────────────────────
// Para no romper código existente que importaba renderTable()
export const renderTable = renderTableIndividual;