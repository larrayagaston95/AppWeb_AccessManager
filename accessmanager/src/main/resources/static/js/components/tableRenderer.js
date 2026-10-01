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

// ── HELPERS DE FORMATEO SIN CONVERSIÓN DE TIMEZONE ───────────────────────────

/**
 * Formatea una cadena de fecha/hora del backend (ISO o 'yyyy-MM-ddTHH:mm:ss')
 * extrayendo la hora directamente con split(), SIN pasar por new Date().
 * @param {string} str - Ej: "2026-10-03T08:00:00" o "2026-10-03 08:00:00"
 * @returns {string} - Ej: "08:00"
 */
function extraerHora(str) {
    if (!str) return '--:--';
    // 1. Separar por la 'T' o el espacio
    const partesFechaHora = str.split(/[T ]/);
    if (partesFechaHora.length < 2) return str; // Si no hay hora, retorna original
    
    // 2. Extraer la parte de la hora "HH:mm:ss..."
    const horaCompleta = partesFechaHora[1];
    
    // 3. Separar por ':' y retornar "HH:mm"
    const partesHora = horaCompleta.split(':');
    if (partesHora.length >= 2) {
        return partesHora[0] + ':' + partesHora[1];
    }
    return horaCompleta;
}

/**
 * Formatea la parte de fecha del string a "dd/mm/aaaa" usando un split puro sin conversión TZ.
 * @param {string} str - Ej: "2026-10-06" o "2026-10-06T00:00:00"
 * @returns {string} - Ej: "06/10/2026"
 */
function extraerFecha(str) {
    if (!str) return '';
    // 1. Tomar solo la parte antes de la 'T' o espacio si lo hubiera
    const fechaPura = str.split(/[T ]/)[0]; // "2026-10-06"
    
    // 2. Separar por guiones
    const partes = fechaPura.split('-'); // ["2026", "10", "06"]
    
    if (partes.length === 3) {
        return partes[2] + '/' + partes[1] + '/' + partes[0];
    }
    return fechaPura;
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
        // ⚠️ NO usar new Date() — parseo manual para evitar offsets de TZ en el browser
        const fechaFmt   = extraerFecha(item.fecha);
        const entradaFmt = extraerHora(item.entrada);
        const salidaFmt  = extraerHora(item.salida);

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
        // ⚠️ NO usar new Date() — parseo manual para evitar offsets de TZ en el browser
        const fechaFmt   = extraerFecha(item.fecha);
        const entradaFmt = extraerHora(item.hora_entrada);
        const salidaFmt  = extraerHora(item.hora_salida);

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

