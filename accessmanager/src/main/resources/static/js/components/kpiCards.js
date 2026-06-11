/**
 * Renderiza dinámicamente las tarjetas de KPIs en el contenedor del panel.
 * @param {number} dias - Cantidad de días procesados en el mes.
 * @param {number} horas - Sumatoria total de horas trabajadas.
 * @param {number} extras - Sumatoria total de horas extras acumuladas.
 */
export function renderKPIs(dias, horas, extras) {
    const contenedor = document.getElementById('contenedorKPI');

    contenedor.innerHTML = `
        <div class="col-md-4">
            <div class="p-3 card-kpi d-flex align-items-center justify-content-between">
                <div>
                    <h6 class="text-muted small fw-bold mb-1">DÍAS PROCESADOS</h6>
                    <h3 class="fw-bold mb-0">${dias}</h3>
                </div>
                <i class="bi bi-calendar-check text-primary fs-1"></i>
            </div>
        </div>
        <div class="col-md-4">
            <div class="p-3 card-kpi d-flex align-items-center justify-content-between">
                <div>
                    <h6 class="text-muted small fw-bold mb-1">HORAS TRABAJADAS</h6>
                    <h3 class="fw-bold mb-0 text-white">${horas.toFixed(2)} hs</h3>
                </div>
                <i class="bi bi-clock-history text-success fs-1"></i>
            </div>
        </div>
        <div class="col-md-4">
            <div class="p-3 card-kpi d-flex align-items-center justify-content-between">
                <div>
                    <h6 class="text-muted small fw-bold mb-1">HORAS EXTRAS ACUMULADAS</h6>
                    <h3 class="fw-bold mb-0 text-turquoise">${extras.toFixed(2)} hs</h3>
                </div>
                <i class="bi bi-lightning-charge-fill text-warning fs-1"></i>
            </div>
        </div>
    `;
}