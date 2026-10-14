/**
 * Escapa un valor para insertarlo de forma segura en HTML (contenido o atributos).
 * Usar SIEMPRE que un dato que viene del servidor o del usuario se interpole en un
 * template que termina en innerHTML: nombres, legajos, observaciones, etc.
 */
export function escapeHtml(valor) {
    if (valor === null || valor === undefined) return '';
    return String(valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
