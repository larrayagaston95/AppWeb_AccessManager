/**
 * Renderiza el encabezado institucional y los filtros horizontales de FLUXTECH.
 * @param {Function} onSalirCallback - Función que se ejecuta al presionar "Salir".
 */
export function renderHeader(onSalirCallback) {
    const headerContainer = document.getElementById('mainHeader');
    if (!headerContainer) return;

    headerContainer.innerHTML = `
        <!-- NAVBAR SUPERIOR INSTITUTIONAL (Tamaño normal y legible) -->
        <nav class="navbar navbar-dark bg-dark border-bottom border-secondary py-3">
            <div class="container-fluid px-4 d-flex align-items-center justify-content-between">

                <!-- Título principal de la plataforma -->
                <div>
                    <span class="navbar-brand mb-0 h1 fs-4 fw-bold text-turquoise">
                        <i class="bi bi-shield-lock-fill me-2"></i>AccessManager
                    </span>
                    <span class="text-muted small ms-2 d-none d-sm-inline">by FLUXTECH</span>
                </div>

                <!-- Bloque de control y Logo Corporativo (Visible en impresión) -->
                <div class="d-flex align-items-center gap-3">

                    <!-- Logo de la Cooperativa / Empresa -->
                    <div class="brand-logo-container d-flex align-items-center border-end border-secondary pe-3">
                        <div class="bg-dark border border-secondary text-turquoise rounded px-3 py-1.5 fw-bold shadow-sm" style="font-size: 12px; letter-spacing: 0.5px;">
                            <i class="bi bi-building me-2"></i>COOPERATIVA CENTRAL
                        </div>
                    </div>

                    <span class="badge bg-dark border border-secondary text-light fw-bold px-3 py-2" style="font-size: 12px;">CLIENTE: KDL</span>

                    <button id="btnPDF" class="btn btn-outline-info btn-sm fw-bold px-3">
                        <i class="bi bi-file-earmark-pdf-fill me-1"></i> Exportar PDF
                    </button>

                    <button id="btnSalir" class="btn btn-outline-danger btn-sm fw-bold px-3">
                        <i class="bi bi-box-arrow-left me-1"></i> Salir
                    </button>
                </div>
            </div>
        </nav>

        <!-- BARRA DE FILTROS ACOMODADA HORIZONTALMENTE -->
        <div class="container-fluid px-4 my-3">
            <div class="p-3 bg-dark-card rounded shadow-sm border border-secondary">
                <div class="row g-3 align-items-center">

                    <!-- Filtro: Sucursal -->
                    <div class="col-6 col-sm-4 col-md-2">
                        <label class="form-label text-muted small fw-bold text-uppercase mb-1" style="font-size: 11px;">Sucursal</label>
                        <select id="selectSucursal" class="form-select bg-dark text-light border-secondary">
                            <option value="todas">-- Todas --</option>
                            <option value="1" selected>Central</option>
                            <option value="2">Sucursal B</option>
                            <option value="3">Sucursal C</option>
                        </select>
                    </div>

                    <!-- Filtro: Sección -->
                    <div class="col-6 col-sm-4 col-md-2">
                        <label class="form-label text-muted small fw-bold text-uppercase mb-1" style="font-size: 11px;">Sección</label>
                        <select id="selectSeccion" class="form-select bg-dark text-light border-secondary">
                            <option value="todas" selected>-- Todas --</option>
                            <option value="telecom">Telecomunicaciones</option>
                            <option value="redes">Redes</option>
                            <option value="gas">Gas</option>
                            <option value="admin">Administración</option>
                        </select>
                    </div>

                    <!-- Filtro: Empleado -->
                    <div class="col-12 col-sm-4 col-md-3">
                        <label class="form-label text-muted small fw-bold text-uppercase mb-1" style="font-size: 11px;">Empleado</label>
                        <select id="selectEmpleado" class="form-select bg-dark text-light border-secondary">
                            <option value="102" selected>Juan Perez (102)</option>
                            <option value="103">Pedro Gomez (103)</option>
                            <option value="104">Maria Lopez (104)</option>
                        </select>
                    </div>

                    <!-- Filtro: Año -->
                    <div class="col-4 col-md-1_5">
                        <label class="form-label text-muted small fw-bold text-uppercase mb-1" style="font-size: 11px;">Año</label>
                        <select id="selectAnio" class="form-select bg-dark text-light border-secondary">
                            <option value="2026" selected>2026</option>
                            <option value="2025">2025</option>
                        </select>
                    </div>

                    <!-- Filtro: Mes -->
                    <div class="col-4 col-md-1_5">
                        <label class="form-label text-muted small fw-bold text-uppercase mb-1" style="font-size: 11px;">Mes</label>
                        <select id="selectMes" class="form-select bg-dark text-light border-secondary">
                            <option value="Junio" selected>Junio</option>
                            <option value="Mayo">Mayo</option>
                        </select>
                    </div>

                    <!-- Botón Filtrar -->
                    <div class="col-4 col-md-1 d-grid">
                        <label class="form-label d-none d-md-block invisible mb-1" style="font-size: 11px;">Acción</label>
                        <button id="btnFiltrar" class="btn btn-turquoise fw-bold">
                            <i class="bi bi-search"></i>
                        </button>
                    </div>

                </div>
            </div>
        </div>
    `;

    // Eventos seguros
    const btnPDF = document.getElementById('btnPDF');
    if (btnPDF) {
        btnPDF.addEventListener('click', () => { window.print(); });
    }

    const btnSalir = document.getElementById('btnSalir');
    if (btnSalir) {
        btnSalir.addEventListener('click', onSalirCallback);
    }
}