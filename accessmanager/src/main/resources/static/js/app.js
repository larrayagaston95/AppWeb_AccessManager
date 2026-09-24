import { fetchReporteMensual, fetchResumenSector, fetchDetalleAsistencia } from './api/apiService.js';
import { initDashboard } from './components/dashboardUI.js';
import { renderHeader }        from './components/headerNav.js';
import { renderFooter }        from './components/footerBar.js';
import { renderKPIs }          from './components/kpiCards.js';
import { renderTableIndividual, renderTableSector, renderizarTablaAsistencia } from './components/tableRenderer.js';
import { inicializarCombosInteligentes } from './components/filtrosHandler.js';
import { exportarIndividual, exportarMasivo } from './services/reporteService.js';
import { initEmpleadosUI } from './components/empleadosUI.js';

// -- Nodos de pantalla --------------------------------------------------------
const vistaApp   = document.getElementById('vistaApp');
const vistaPanel = document.getElementById('vistaPanel');

// Mapa de texto de mes ? número
const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

// ============================================================================
// 1. INICIALIZACIÓN DE ESTRUCTURA UI (UNA SOLA VEZ por sesión)
// ============================================================================
async function inicializarEstructuraUI() {
    renderHeader(ejecutarCierreSesion);
    renderFooter();
    vincularBtnFiltrarHeader();
    vincularNavegacionSPA();
    await inicializarCombosInteligentes();
    await initEmpleadosUI();
    await initDashboard();       // Módulo 1: Dashboard en tiempo real
    actualizarFechaDashboard();  // Muestra la fecha de hoy en el encabezado
}

/**
 * Conecta los tabs de navegación principal
 */
/**
 * Helpers de navegación SPA: activa un tab y desactiva los demás.
 */
function setTabActivo(idTab) {
    ['navDashboard', 'navAsistencia', 'navPersonal'].forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        if (id === idTab) {
            el.classList.add('active');
            el.classList.remove('text-light');
        } else {
            el.classList.remove('active');
            el.classList.add('text-light');
        }
    });
}

function mostrarVista(vistaId) {
    const vistas = ['vistaDashboard', 'vistaPanel', 'vistaEmpleados', 'vistaRelojes'];
    vistas.forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;
        if (id === vistaId) {
            el.classList.remove('d-none');
            if (id === 'vistaEmpleados' || id === 'vistaRelojes') el.classList.add('d-flex');
        } else {
            el.classList.add('d-none');
            if (id === 'vistaEmpleados' || id === 'vistaRelojes') el.classList.remove('d-flex');
        }
    });
    // La barra de filtros de asistencia solo es visible en vistaPanel
    const barraFiltros = document.getElementById('barraFiltrosAsistencia');
    const contenedorKPI = document.getElementById('contenedorKPI');
    if (barraFiltros) barraFiltros.classList.toggle('d-none', vistaId !== 'vistaPanel');
    if (contenedorKPI) contenedorKPI.classList.toggle('d-none', vistaId !== 'vistaPanel');
}

function vincularNavegacionSPA() {
    const navDashboard  = document.getElementById('navDashboard');
    const navAsistencia = document.getElementById('navAsistencia');
    const navPersonal   = document.getElementById('navPersonal');

    navDashboard?.addEventListener('click', (e) => {
        e.preventDefault();
        setTabActivo('navDashboard');
        mostrarVista('vistaDashboard');
    });

    navAsistencia?.addEventListener('click', (e) => {
        e.preventDefault();
        setTabActivo('navAsistencia');
        mostrarVista('vistaPanel');
    });

    navPersonal?.addEventListener('click', (e) => {
        e.preventDefault();
        setTabActivo('navPersonal');
        mostrarVista('vistaEmpleados');
    });
}

/**
 * Conecta el botón #btnFiltrar del header dinámico.
 * Se llama después de renderHeader() con flag anti-duplicado.
 */
function vincularBtnFiltrarHeader() {
    const btn = document.getElementById('btnFiltrar');
    if (btn && !btn.dataset.bound) {
        btn.addEventListener('click', () => actualizarPanel());
        btn.dataset.bound = 'true';
    }
}

// ============================================================================
// 2. LEER VALORES DE LOS FILTROS ACTIVOS
// ============================================================================
function leerFiltros() {
    const selectEmpleado = document.getElementById('selectEmpleado');
    const selectSeccion  = document.getElementById('selectSeccion');
    const selectAnio     = document.getElementById('selectAnio');
    const selectMes      = document.getElementById('selectMes');

    return {
        legajo:     selectEmpleado ? selectEmpleado.value.trim() : '',
        sectorId:   selectSeccion  ? selectSeccion.value.trim()  : '',
        anio:       selectAnio     ? selectAnio.value             : '2026',
        mesTexto:   selectMes      ? selectMes.value              : 'Junio',
        mesNumero:  mapaMeses[selectMes ? selectMes.value : 'Junio'] || 6
    };
}

// ============================================================================
// 3. ACTUALIZAR PANEL — Lógica dual:
//    - Con legajo ? Modo Individual (fichadas diarias)
//    - Sin legajo ? Modo Sector     (resumen consolidado)
// ============================================================================
async function actualizarPanel() {
    const { legajo, sectorId, anio, mesNumero } = leerFiltros();

    // Determinamos el modo según si hay empleado seleccionado
    const modoIndividual = legajo !== '';

    if (!modoIndividual && !sectorId) {
        alert('Por favor seleccione al menos una Sección para consultar el resumen del sector.');
        return;
    }

    // Aseguramos que el panel general esté visible
    vistaApp.classList.remove('d-none');

    try {
        if (modoIndividual) {
            // -- MODO INDIVIDUAL ----------------------------------------------
            const data = await fetchDetalleAsistencia(legajo, anio, mesNumero);

            let totalHoras  = 0;
            let totalExtras = 0;
            data.forEach(item => {
                totalHoras  += item.horas_trabajadas ?? 0;
                totalExtras += item.horas_extras     ?? 0;
            });

            renderKPIs(data.length, totalHoras, totalExtras);
            renderizarTablaAsistencia(data);

        } else {
            // -- MODO SECTOR (resumen consolidado) ----------------------------
            const data = await fetchResumenSector(sectorId, anio, mesNumero);

            // KPIs del sector: sumamos totales de todos los empleados
            const totalDias   = data.reduce((acc, emp) => acc + (emp.diasTrabajados ?? 0), 0);
            const totalHoras  = data.reduce((acc, emp) => acc + (emp.totalHoras     ?? 0), 0);
            const totalExtras = data.reduce((acc, emp) => acc + (emp.totalExtras    ?? 0), 0);

            renderKPIs(totalDias, totalHoras, totalExtras);
            renderTableSector(data);
        }

    } catch (error) {
        if (error.message === 'UNAUTHORIZED' || error.message === 'FORBIDDEN') {
            ejecutarCierreSesion();
        } else {
            console.error('? Error en AccessManager:', error);
            alert('No se pudo establecer comunicación con el servidor.');
        }
    }
}

// ============================================================================
// 4. CIERRE DE SESIÓN
// ============================================================================
function ejecutarCierreSesion() {
    localStorage.removeItem('access_token_am');
    localStorage.removeItem('username_am');
    localStorage.removeItem('rol_am');
    localStorage.removeItem('empresa_id_am');
    window.location.href = 'login.html';
}

// ============================================================================
// 5. EVENTOS DEL DOM ESTÁTICO (elementos en index.html)
// ============================================================================

// Botón Exportar PDF Individual
const btnExportarIndividual = document.getElementById('btnExportarIndividual');
if (btnExportarIndividual) {
    btnExportarIndividual.addEventListener('click', exportarIndividual);
}

// Botón Reporte Masivo — pasa año y mes en el momento del clic
const btnReporteMasivo = document.getElementById('btnReporteMasivo');
if (btnReporteMasivo) {
    btnReporteMasivo.addEventListener('click', () => {
        const { anio, mesNumero } = leerFiltros();
        exportarMasivo(anio, mesNumero);
    });
}

// ============================================================================
// 6. ARRANQUE AUTOMÁTICO si ya hay token (recarga de página)
// ============================================================================
/**
 * Muestra la fecha de hoy en el encabezado del Dashboard.
 */
function actualizarFechaDashboard() {
    const el = document.getElementById('dashFechaHoy');
    if (el) {
        el.textContent = new Date().toLocaleDateString('es-AR', {
            weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
        });
    }
}

window.addEventListener('DOMContentLoaded', async () => {
    if (localStorage.getItem('access_token_am')) {
        vistaApp.classList.remove('d-none');
        await inicializarEstructuraUI();

        // La pantalla inicial es el Dashboard (no el panel de asistencia)
        mostrarVista('vistaDashboard');
        setTabActivo('navDashboard');

        // Botón 'Ver Asistencia' dentro del Dashboard
        document.getElementById('btnIrAsistencia')?.addEventListener('click', () => {
            setTabActivo('navAsistencia');
            mostrarVista('vistaPanel');
        });

        // Botón Refresh manual del Dashboard
        document.getElementById('btnRefreshDashboard')?.addEventListener('click', () => {
            const sucursalId = document.getElementById('filtroDashboardSucursal')?.value;
            // Re-dispara el change event para reutilizar la lógica del listener
            const select = document.getElementById('filtroDashboardSucursal');
            if (select) select.dispatchEvent(new Event('change'));
        });

    } else {
        window.location.href = 'login.html';
    }
});

// ============================================================================
// 7. CARGA DE FICHADAS OFFLINE
// ============================================================================
const modalSubirOffline = document.getElementById('modalSubirOffline');
const selectOfflineSucursal = document.getElementById('offlineSucursal');
const formSubirOffline = document.getElementById('formSubirOffline');

if (modalSubirOffline) {
    modalSubirOffline.addEventListener('show.bs.modal', async () => {
        if (selectOfflineSucursal && selectOfflineSucursal.options.length <= 1) {
            try {
                // fetchSucursales might be imported from apiService.js, if not it will fail
                const { fetchSucursales } = await import('./api/apiService.js');
                const sucursales = await fetchSucursales();
                selectOfflineSucursal.innerHTML = '<option value=\"\">-- Seleccione Sucursal --</option>';
                sucursales.forEach(suc => {
                    const option = document.createElement('option');
                    option.value = suc.id || suc.idsucursal;
                    option.textContent = suc.nombre;
                    selectOfflineSucursal.appendChild(option);
                });
            } catch (error) {
                console.error('Error cargando sucursales:', error);
                alert('No se pudieron cargar las sucursales.');
            }
        }
    });
}

if (formSubirOffline) {
    formSubirOffline.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const sucursalSelect = document.getElementById('offlineSucursal');
        const sucursalId = sucursalSelect ? sucursalSelect.value : '';
        const fileInput = document.getElementById('offlineFile');
        const file = fileInput.files[0];

        if (!sucursalId) {
            alert('Por favor, seleccione una sucursal antes de subir el archivo.');
            return;
        }

        if (!file) {
            alert('Por favor seleccione un archivo.');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);
        formData.append('sucursalId', sucursalId);

        const token = localStorage.getItem('access_token_am');
        try {
            const response = await fetch('/api/fichadas/upload', {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + token
                },
                body: formData
            });

            if (response.ok) {
                const text = await response.text();
                alert(text || 'Archivo subido correctamente.');
                
                if (typeof bootstrap !== 'undefined') {
                    const modal = bootstrap.Modal.getInstance(modalSubirOffline);
                    if (modal) modal.hide();
                }
                formSubirOffline.reset();
            } else {
                const errorText = await response.text();
                alert('Error: ' + errorText);
            }
        } catch (error) {
            console.error('Error al subir el archivo:', error);
            alert('Error de red al subir el archivo.');
        }
    });
}



// Logic for Limpiar Memoria
const btnLimpiarMemoria = document.getElementById('btnLimpiarMemoria');
if (btnLimpiarMemoria) {
    btnLimpiarMemoria.addEventListener('click', async () => {
        const relojId = prompt('Ingrese el ID del Reloj para limpiar su memoria (CLEAR ATTLOG):');
        if (!relojId) return;

        if (!confirm('¿Está seguro de que desea limpiar la memoria física de este reloj? Esto borrará todos los fichajes almacenados en el dispositivo. Asegúrese de que el reloj esté al día.')) {
            return;
        }

        const token = localStorage.getItem('access_token_am');
        try {
            const response = await fetch('/api/relojes/' + relojId + '/limpiar-memoria', {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + token
                }
            });

            if (response.ok) {
                const res = await response.json();
                alert(res.message || 'Comando encolado correctamente.');
            } else {
                const err = await response.json();
                alert('Error: ' + (err.error || 'No se pudo encolar el comando.'));
            }
        } catch (error) {
            console.error('Error:', error);
            alert('Error de red al intentar limpiar la memoria.');
        }
    });
}

