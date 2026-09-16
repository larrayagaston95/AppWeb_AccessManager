/**
 * dashboardUI.js
 * Renderiza las 4 tarjetas de KPI del Dashboard de asistencia en tiempo real
 * y gestiona el filtro por Sucursal.
 */

import { fetchResumenDashboard } from '../services/dashboardService.js';
import { fetchSucursales } from '../api/apiService.js';

// ─────────────────────────────────────────────────────────────────────────────
// Renderizado de tarjetas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dibuja las 4 tarjetas KPI con animación de número y código de color.
 * @param {{totalEmpleados, presentes, ausentes, llegadasTarde}} data
 */
function renderDashboardCards(data) {
    const container = document.getElementById('kpisDashboard');
    if (!container) return;

    const { totalEmpleados, presentes, ausentes, llegadasTarde } = data;

    container.innerHTML = `
        <!-- Total Personal -->
        <div class="col-12 col-sm-6 col-xl-3">
            <div class="card-dashboard card-dashboard--blue">
                <div class="card-dashboard__icon">
                    <i class="bi bi-people-fill"></i>
                </div>
                <div class="card-dashboard__body">
                    <span class="card-dashboard__label">Total Personal</span>
                    <span class="card-dashboard__value" id="kpiTotal">${totalEmpleados}</span>
                    <span class="card-dashboard__sub">Empleados registrados</span>
                </div>
            </div>
        </div>

        <!-- Presentes -->
        <div class="col-12 col-sm-6 col-xl-3">
            <div class="card-dashboard card-dashboard--green">
                <div class="card-dashboard__icon">
                    <i class="bi bi-person-check-fill"></i>
                </div>
                <div class="card-dashboard__body">
                    <span class="card-dashboard__label">🟢 Presentes Hoy</span>
                    <span class="card-dashboard__value" id="kpiPresentes">${presentes}</span>
                    <span class="card-dashboard__sub">${totalEmpleados > 0 ? Math.round((presentes / totalEmpleados) * 100) : 0}% de asistencia</span>
                </div>
            </div>
        </div>

        <!-- Ausentes -->
        <div class="col-12 col-sm-6 col-xl-3">
            <div class="card-dashboard card-dashboard--red">
                <div class="card-dashboard__icon">
                    <i class="bi bi-person-x-fill"></i>
                </div>
                <div class="card-dashboard__body">
                    <span class="card-dashboard__label">🔴 Ausentes</span>
                    <span class="card-dashboard__value" id="kpiAusentes">${ausentes}</span>
                    <span class="card-dashboard__sub">Sin registro de entrada</span>
                </div>
            </div>
        </div>

        <!-- Llegadas Tarde -->
        <div class="col-12 col-sm-6 col-xl-3">
            <div class="card-dashboard card-dashboard--yellow">
                <div class="card-dashboard__icon">
                    <i class="bi bi-alarm-fill"></i>
                </div>
                <div class="card-dashboard__body">
                    <span class="card-dashboard__label">⚠️ Llegadas Tarde</span>
                    <span class="card-dashboard__value" id="kpiTarde">${llegadasTarde}</span>
                    <span class="card-dashboard__sub">Superaron horario base</span>
                </div>
            </div>
        </div>
    `;
}

/**
 * Muestra un estado de carga mientras se piden los datos al backend.
 */
function renderDashboardSkeleton() {
    const container = document.getElementById('kpisDashboard');
    if (!container) return;
    const skeleton = `
        <div class="col-12 col-sm-6 col-xl-3">
            <div class="card-dashboard card-dashboard--blue skeleton-card">
                <div class="skeleton-icon"></div>
                <div class="card-dashboard__body">
                    <span class="skeleton-line w-50"></span>
                    <span class="skeleton-line w-25 mt-2" style="height:36px;"></span>
                    <span class="skeleton-line w-75 mt-2"></span>
                </div>
            </div>
        </div>`;
    container.innerHTML = skeleton.repeat(4);
}

// ─────────────────────────────────────────────────────────────────────────────
// Carga de datos y listeners
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carga los datos del dashboard para una sucursal dada (o toda la empresa).
 * @param {number|null} sucursalId
 */
async function cargarDatosDashboard(sucursalId) {
    renderDashboardSkeleton();
    try {
        const data = await fetchResumenDashboard(sucursalId || null);
        renderDashboardCards(data);
    } catch (err) {
        const container = document.getElementById('kpisDashboard');
        if (container) {
            container.innerHTML = `
                <div class="col-12">
                    <div class="alert alert-danger d-flex align-items-center gap-2 border-0 rounded-3">
                        <i class="bi bi-exclamation-triangle-fill fs-5"></i>
                        <span>No se pudo conectar con el servidor. Verificá que el backend esté activo.</span>
                    </div>
                </div>`;
        }
        console.error('Error cargando dashboard:', err);
    }
}

/**
 * Puebla el <select id="filtroDashboardSucursal"> con las sucursales de la empresa.
 */
async function cargarSucursalesEnFiltro() {
    const select = document.getElementById('filtroDashboardSucursal');
    if (!select) return;

    try {
        const sucursales = await fetchSucursales();
        select.innerHTML = `<option value="">🏢 Todas las Sucursales</option>`;
        sucursales.forEach(suc => {
            const opt = document.createElement('option');
            opt.value = suc.id;
            opt.textContent = suc.nombre;
            select.appendChild(opt);
        });
    } catch (err) {
        console.error('Error cargando sucursales en dashboard:', err);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Punto de entrada público
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Inicializa el Dashboard:
 *   1. Carga las sucursales en el filtro.
 *   2. Carga los datos iniciales (toda la empresa).
 *   3. Registra el listener del select para actualizar en tiempo real.
 */
export async function initDashboard() {
    // Cargamos las sucursales y los datos al mismo tiempo (Promise.all para velocidad)
    await Promise.all([
        cargarSucursalesEnFiltro(),
        cargarDatosDashboard(null)
    ]);

    // Listener: onchange del selector de sucursales
    const select = document.getElementById('filtroDashboardSucursal');
    if (select && !select.dataset.bound) {
        select.addEventListener('change', () => {
            const sucursalId = select.value ? parseInt(select.value) : null;
            cargarDatosDashboard(sucursalId);
        });
        select.dataset.bound = 'true';
    }
}
