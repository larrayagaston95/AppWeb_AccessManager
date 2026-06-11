/**
 * Renderiza el pie de página corporativo de forma dinámica.
 */
export function renderFooter() {
    const footerContainer = document.getElementById('mainFooter');
    const anioActual = new Date().getFullYear();

    footerContainer.innerHTML = `
        <div class="container-fluid text-center py-3 text-muted small">
            <span>&copy; ${anioActual} <strong>VoltechDevs</strong>. Todos los derechos reservados.</span>
            <span class="mx-2">|</span>
            <span class="text-turquoise">AccessManager v1.0</span>
        </div>
    `;
}