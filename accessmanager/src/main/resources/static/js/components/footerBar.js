/**
 * Renderiza el pie de página corporativo de forma dinámica.
 */
export function renderFooter() {
    const footerContainer = document.getElementById('mainFooter');
    const anioActual = new Date().getFullYear();

    footerContainer.innerHTML = `
        <div class="container-fluid text-center py-3 text-white-50 small">
            <span>&copy; ${anioActual} <strong class="text-light">FluxTech</strong>. Todos los derechos reservados.</span>
            <span class="mx-2 text-white-50">|</span>
            <span class="text-turquoise fw-semibold">AccessManager v1.0</span>
        </div>
    `;
}