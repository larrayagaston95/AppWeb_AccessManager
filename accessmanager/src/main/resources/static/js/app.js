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
 * Función principal que pide la data al backend y coordina el renderizado modular.
 */
async function actualizarPanel() {
    const legajo = document.getElementById('selectEmpleado').value;
    const anio = document.getElementById('selectAnio').value;
    const mes = document.getElementById('selectMes').value;

    try {
        const data = await fetchReporteMensual(legajo, anio, mes);

        // Si la respuesta es correcta, estructuramos la vista
        vistaLogin.classList.add('d-none');
        vistaPanel.classList.remove('d-none');
        errorMsg.classList.add('d-none');

        // Inicializamos los componentes estáticos fijando la firma corporativa
        renderHeader(ejecutarCierreSesion);
        renderFooter();

        // Procesamos las métricas acumuladas (KPIs)
        let totalHoras = 0;
        let totalExtras = 0;
        data.forEach(item => {
            totalHoras += item.horasTrabajadas;
            totalExtras += item.horasExtras;
        });

        // Dibujamos la interfaz con la data real
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

/**
 * Limpia el token del navegador y resetea las vistas al estado inicial.
 */
function ejecutarCierreSesion() {
    localStorage.removeItem('access_token_am');
    document.getElementById('inputToken').value = '';
    vistaPanel.classList.add('d-none');
    vistaLogin.classList.remove('d-none');
}

// --- Vinculación de Eventos del DOM ---

// Evento para el botón Ingresar del Login
document.getElementById('btnIngresar').addEventListener('click', () => {
    const token = document.getElementById('inputToken').value;
    localStorage.setItem('access_token_am', token);
    actualizarPanel();
});

// Evento para el botón Filtrar del Panel
document.getElementById('btnFiltrar').addEventListener('click', actualizarPanel);

// Al cargar la app, verifica si ya existía una sesión guardada
window.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('access_token_am')) {
        actualizarPanel();
    }
});