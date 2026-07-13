import { fetchReporteMensual } from './api/apiService.js';
import { renderHeader } from './components/headerNav.js';
import { renderFooter } from './components/footerBar.js';
import { renderKPIs } from './components/kpiCards.js';
import { renderTable } from './components/tableRenderer.js';

// 🔌 NUEVAS IMPORTACIONES MODULARES
import { inicializarCombosInteligentes } from './components/filtrosHandler.js';
import { exportarIndividual, exportarMasivo } from './services/reporteService.js';

// Nodos del DOM para controlar pantallas
const vistaLogin = document.getElementById('vistaLogin');
const vistaPanel = document.getElementById('vistaPanel');
const errorMsg = document.getElementById('loginError');

const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

/**
 * 🔍 FUNCIÓN DE FILTRADO - Actualiza la grilla y KPIs en pantalla
 */
async function actualizarPanel() {
    const selectEmpleado = document.getElementById('selectEmpleado');
    const selectAnio = document.getElementById('selectAnio');
    const selectMes = document.getElementById('selectMes');

    const legajo = selectEmpleado ? selectEmpleado.value : '';
    const anio = selectAnio ? selectAnio.value : '2026';
    const mesTexto = selectMes ? selectMes.value : 'Junio';

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

document.getElementById('btnFiltrar').addEventListener('click', () => actualizarPanel());

// 🚀 Vinculamos los botones unificados a las funciones importadas
document.getElementById('btnExportarIndividual').addEventListener('click', exportarIndividual);
document.getElementById('btnReporteMasivo').addEventListener('click', exportarMasivo);

// =========================================================================
// 🚀 INICIALIZADOR ÚNICO DE CARGA
// =========================================================================
window.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('access_token_am')) {
        actualizarPanel();
        // Encendemos Select2 con un leve delay para asegurar el render dinámico del Header
        setTimeout(inicializarCombosInteligentes, 300);
    }
});