import { fetchReporteMensual } from './api/apiService.js';
import { renderHeader } from './components/headerNav.js';
import { renderFooter } from './components/footerBar.js';
import { renderKPIs } from './components/kpiCards.js';
import { renderTable } from './components/tableRenderer.js';

// Nodos del DOM para controlar pantallas
const vistaLogin = document.getElementById('vistaLogin');
const vistaPanel = document.getElementById('vistaPanel');
const errorMsg = document.getElementById('loginError');

/**
 * TU FUNCIÓN DE FILTRADO ORIGINAL - INTACTA, NO SE TOCO NADA
 */
async function actualizarPanel(event) {
    let contenedorFiltros = document;

    if (event && event.target) {
        contenedorFiltros = event.target.closest('.card') || event.target.closest('main') || document;
    }

    const selectEmpleado = contenedorFiltros.querySelector('#selectEmpleado');
    const selectAnio = contenedorFiltros.querySelector('#selectAnio');
    const selectMes = contenedorFiltros.querySelector('#selectMes');

    const legajo = selectEmpleado ? selectEmpleado.value : '';
    const anio = selectAnio ? selectAnio.value : '2026';
    const mesTexto = selectMes ? selectMes.value : 'Junio';

    const mapaMeses = {
        "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
        "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
    };
    const mesNumero = mapaMeses[mesTexto] || 6;

    if (!legajo) {
        renderHeader(ejecutarCierreSesion);
        renderFooter();

        vistaLogin.classList.add('d-none');
        vistaPanel.classList.remove('d-none');
        errorMsg.classList.add('d-none');
        return;
    }

    try {
        const data = await fetchReporteMensual(legajo, anio, mesNumero);

        vistaLogin.classList.add('d-none');
        vistaPanel.classList.remove('d-none');
        errorMsg.classList.add('d-none');

        renderHeader(ejecutarCierreSesion);
        renderFooter();

        let totalHoras = 0;
        let totalExtras = 0;
        data.forEach(item => {
            totalHoras += item.horasTrabajadas;
            totalExtras += item.horasExtras;
        });

        renderKPIs(data.length, totalHoras, totalExtras);
        renderTable(data);

    } catch (error) {
        if (error.message === 'UNAUTHORIZED') {
            ejecutarCierreSesion();
            errorMsg.classList.remove('d-none');
        } else {
            console.error('Error operativo en AccessManager:', error);
            alert('No se pudo establecer comunicación con el servidor.');
        }
    }
}

function ejecutarCierreSesion() {
    localStorage.removeItem('access_token_am');
    const inputToken = document.getElementById('inputToken');
    if (inputToken) inputToken.value = '';
    vistaPanel.classList.add('d-none');
    vistaLogin.classList.remove('d-none');
}

// --- Vinculación de Eventos del DOM ---

document.getElementById('btnIngresar').addEventListener('click', () => {
    const token = document.getElementById('inputToken').value;
    localStorage.setItem('access_token_am', token);
    actualizarPanel();
});

document.getElementById('btnFiltrar').addEventListener('click', (e) => actualizarPanel(e));


// =========================================================================
// 📄 LOGICA DEL BOTÓN PDF (Modificado de forma segura sin tocar el filtro)
// =========================================================================
document.addEventListener('click', (e) => {
    // Detectamos si hicieron clic en el botón PDF (por ID o por su clase e icono)
    const targetBoton = e.target.closest('#btnPDF') ||
                       (e.target.classList && e.target.classList.contains('btn-danger') && e.target.innerText.includes('PDF') ? e.target : null);

    if (targetBoton) {
        e.preventDefault();
        e.stopPropagation();

        // 🛠️ COPIAMOS LA LÓGICA DE TU FILTRADO QUE SÍ FUNCIONA: Buscamos la zona activa
        let contenedorFiltros = document;
        if (e && e.target) {
            contenedorFiltros = e.target.closest('.card') || e.target.closest('main') || document;
        }

        // Buscamos los combos de manera segura adentro de ese contenedor exacto
        const selectEmpleado = contenedorFiltros.querySelector('#selectEmpleado') || document.querySelector('select');
        const selectAnio = contenedorFiltros.querySelector('#selectAnio');
        const selectMes = contenedorFiltros.querySelector('#selectMes');

        // Extraemos los valores con salvavidas por si alguno da nulo
        const legajo = selectEmpleado ? selectEmpleado.value : '';
        const anio = selectAnio ? selectAnio.value : '2026';
        const mesTexto = selectMes ? selectMes.value : 'Junio';

        // Captura segura del nombre del empleado para la cabecera de Jasper
        const nombreEmpleado = (selectEmpleado && selectEmpleado.selectedIndex !== -1)
            ? selectEmpleado.options[selectEmpleado.selectedIndex].text.split('(')[0].trim()
            : "Empleado";

        // Si el legajo no se pudo leer, tiramos el aviso en el front en lugar de dejar que explote Java
        if (!legajo || legajo.trim() === "") {
            alert('Por favor, asegúrese de seleccionar un empleado en el combo antes de exportar.');
            return;
        }

        // Traducción limpia de Mes a número entero
        const mapaMeses = {
            "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
            "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
        };
        const mesNumero = mapaMeses[mesTexto] || 6;

        // Armamos la URL exacta macheada con tu AsistenciaReportController
        const urlReporte = `/api/reportes/asistencia?legajo=${legajo}&anio=${anio}&mes=${mesNumero}&nombreEmpleado=${encodeURIComponent(nombreEmpleado)}`;

        console.log("📥 Descargando reporte PDF desde: " + urlReporte);

       // Nuevo (Abre una pestaña nueva limpia con el visor de Chrome)
       window.open(urlReporte, '_blank');
    }
});

window.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('access_token_am')) {
        actualizarPanel();
    }
});