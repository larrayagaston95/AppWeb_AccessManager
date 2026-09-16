/**
 * Renderiza el encabezado institucional con la barra de filtros.
 * Los <select> de Sucursal, Sección y Empleado arrancan VACÍOS;
 * su contenido real lo inyecta inicializarCombosInteligentes().
 *
 * @param {Function} onSalirCallback - Se ejecuta al presionar "Salir".
 */
export function renderHeader(onSalirCallback) {
    const headerContainer = document.getElementById('mainHeader');
    if (!headerContainer) return;

    headerContainer.innerHTML = `
        <!-- NAVBAR SUPERIOR INSTITUCIONAL -->
        <nav class="navbar navbar-dark bg-dark border-bottom border-secondary py-3">
            <div class="container-fluid px-4 d-flex align-items-center justify-content-between">

                <!-- Título principal de la plataforma -->
                <div class="d-flex align-items-center">
                    <span class="navbar-brand mb-0 h1 fs-4 fw-bold text-turquoise me-4">
                        <i class="bi bi-shield-lock-fill me-2"></i>AccessManager
                    </span>
                    
                    <!-- Menú de Navegación SPA -->
                    <ul class="nav nav-pills d-none d-md-flex" id="mainNavigation">
                        <li class="nav-item">
                            <a class="nav-link active px-3 py-1 fw-bold" id="navDashboard" href="#" style="border-radius: 20px;">
                                <i class="bi bi-speedometer2 me-1"></i> Dashboard
                            </a>
                        </li>
                        <li class="nav-item ms-2">
                            <a class="nav-link text-light px-3 py-1 fw-bold" id="navAsistencia" href="#" style="border-radius: 20px;">
                                <i class="bi bi-calendar-check me-1"></i> Asistencia
                            </a>
                        </li>
                        <li class="nav-item ms-2">
                            <a class="nav-link text-light px-3 py-1 fw-bold" id="navPersonal" href="#" style="border-radius: 20px;">
                                <i class="bi bi-people me-1"></i> Personal
                            </a>
                        </li>
                    </ul>
                </div>

                <!-- Bloque de control y Logo Corporativo -->
                <div class="d-flex align-items-center gap-3">


                    <span class="badge bg-dark border border-secondary text-light fw-bold px-3 py-2"
                          style="font-size: 12px;">CLIENTE: KDL</span>

                    <button id="btnSalir" class="btn btn-outline-danger btn-sm fw-bold px-3">
                        <i class="bi bi-box-arrow-left me-1"></i> Salir
                    </button>
                </div>
            </div>
        </nav>

        <!-- BARRA DE FILTROS EN CASCADA (Solo visible en Asistencia) -->
        <div class="container-fluid px-4 my-3" id="barraFiltrosAsistencia">
            <div class="p-3 bg-dark-card rounded shadow-sm border border-secondary">
                <div class="row g-3 align-items-end">

                    <!-- Filtro: Sucursal -->
                    <div class="col-6 col-sm-4 col-md-2">
                        <label class="form-label text-light small fw-bold text-uppercase mb-1"
                               style="font-size: 11px;">Sucursal</label>
                        <select id="selectSucursal" class="form-select bg-dark text-light border-secondary">
                            <option value="">-- Cargando... --</option>
                        </select>
                    </div>

                    <!-- Filtro: Sección (se puebla según la sucursal elegida) -->
                    <div class="col-6 col-sm-4 col-md-2">
                        <label class="form-label text-light small fw-bold text-uppercase mb-1"
                               style="font-size: 11px;">Sección</label>
                        <select id="selectSeccion" class="form-select bg-dark text-light border-secondary">
                            <option value="">-- Seleccione Sección --</option>
                        </select>
                    </div>

                    <!-- Filtro: Empleado (se puebla según la sección elegida) -->
                    <div class="col-12 col-sm-4 col-md-3">
                        <label class="form-label text-light small fw-bold text-uppercase mb-1"
                               style="font-size: 11px;">Empleado</label>
                        <select id="selectEmpleado" class="form-select bg-dark text-light border-secondary">
                            <option value="">-- Seleccione Empleado --</option>
                        </select>
                    </div>

                    <!-- Filtro: Año -->
                    <div class="col-4 col-md-2">
                        <label class="form-label text-light small fw-bold text-uppercase mb-1"
                               style="font-size: 11px;">Año</label>
                        <select id="selectAnio" class="form-select bg-dark text-light border-secondary">
                            <option value="2026" selected>2026</option>
                            <option value="2025">2025</option>
                            <option value="2024">2024</option>
                        </select>
                    </div>

                    <!-- Filtro: Mes -->
                    <div class="col-4 col-md-2">
                        <label class="form-label text-light small fw-bold text-uppercase mb-1"
                               style="font-size: 11px;">Mes</label>
                        <select id="selectMes" class="form-select bg-dark text-light border-secondary">
                            <option value="Enero">Enero</option>
                            <option value="Febrero">Febrero</option>
                            <option value="Marzo">Marzo</option>
                            <option value="Abril">Abril</option>
                            <option value="Mayo">Mayo</option>
                            <option value="Junio" selected>Junio</option>
                            <option value="Julio">Julio</option>
                            <option value="Agosto">Agosto</option>
                            <option value="Septiembre">Septiembre</option>
                            <option value="Octubre">Octubre</option>
                            <option value="Noviembre">Noviembre</option>
                            <option value="Diciembre">Diciembre</option>
                        </select>
                    </div>

                    <!-- Botón Buscar -->
                    <div class="col-4 col-md-1 d-grid">
                        <label class="form-label d-none d-md-block invisible mb-1"
                               style="font-size: 11px;">Acción</label>
                        <button id="btnFiltrar" class="btn btn-turquoise fw-bold">
                            <i class="bi bi-search"></i>
                        </button>
                    </div>

                </div>
            </div>
        </div>
    `;

    // Conectamos el botón Salir
    const btnSalir = document.getElementById('btnSalir');
    if (btnSalir) {
        btnSalir.addEventListener('click', onSalirCallback);
    }
}
