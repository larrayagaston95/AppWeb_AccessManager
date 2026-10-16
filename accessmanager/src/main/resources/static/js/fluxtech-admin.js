/**
 * fluxtech-admin.js
 * Logica del panel SuperAdmin de FluxTech.
 * Gestiona: Alta de Clientes, Sucursales y Sectores.
 */

const BASE = '/api/superadmin';

document.addEventListener('DOMContentLoaded', async () => {

    // ─── Verificacion de sesion y rol ────────────────────────────────────────
    const token = localStorage.getItem('access_token_am');
    const rol   = localStorage.getItem('rol_am');

    if (!token || rol !== 'ROLE_SUPERADMIN') {
        localStorage.clear();
        window.location.href = 'login.html';
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Muestra feedback inline en la tarjeta en vez de alert(). */
    function showToast(id, message, isError = false) {
        const el = document.getElementById(id);
        if (!el) return;
        el.textContent = message;
        el.className = `sa-toast show alert ${isError ? 'alert-danger' : 'alert-success'} border-0`;
        setTimeout(() => { el.className = 'sa-toast'; }, 6000);
    }

    /** Carga un <select> con opciones. */
    function populateSelect(selectId, items, labelFn) {
        const sel = document.getElementById(selectId);
        if (!sel) return;
        sel.innerHTML = '<option value="">-- Seleccione --</option>';
        items.forEach(item => {
            const opt = document.createElement('option');
            opt.value = item.id;
            opt.textContent = labelFn(item);
            sel.appendChild(opt);
        });
    }

    // ─── Logout ──────────────────────────────────────────────────────────────
    document.getElementById('btnCerrarSesion').addEventListener('click', () => {
        localStorage.clear();
        window.location.href = 'login.html';
    });

    let empresasCache = [];
    let sucursalesCache = [];

    // ─── Cargar empresas en el select de Alta Sucursal ───────────────────────
    async function cargarEmpresas() {
        try {
            const resp = await fetch(`${BASE}/empresas`, { headers });
            if (!resp.ok) throw new Error('No se pudo cargar la lista de empresas');
            const empresas = await resp.json();
            empresasCache = empresas;
            populateSelect('empresaSelect', empresas, e => `${e.nombre}  (ID: ${e.id})`);
            populateSelect('agenteEmpresaSelect', empresas, e => `${e.nombre}  (ID: ${e.id})`);
        } catch (err) {
            const sel = document.getElementById('empresaSelect');
            if (sel) sel.innerHTML = '<option value="">Error al cargar empresas</option>';
            console.error(err);
        }
    }

    // ─── Cargar sucursales en el select de Alta Sector ───────────────────────
    async function cargarSucursales() {
        try {
            const resp = await fetch(`${BASE}/sucursales`, { headers });
            if (!resp.ok) throw new Error('No se pudo cargar la lista de sucursales');
            const sucursales = await resp.json();
            sucursalesCache = sucursales;
            populateSelect(
                'sucursalSelect',
                sucursales,
                s => `${s.nombre}  (${s.empresa || 'Sin empresa'})`
            );
        } catch (err) {
            const sel = document.getElementById('sucursalSelect');
            if (sel) sel.innerHTML = '<option value="">Error al cargar sucursales</option>';
            console.error(err);
        }
    }

    // Cargamos ambos selects al arrancar
    await Promise.all([cargarEmpresas(), cargarSucursales()]);

    // ─── Formulario 1: Crear Cliente ─────────────────────────────────────────
    document.getElementById('formCrearCliente').addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = document.getElementById('btnCrearCliente');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Creando...';

        const payload = {
            nombre_empresa: document.getElementById('empNombre').value.trim(),
            username_admin: document.getElementById('adminUser').value.trim(),
            password_admin: document.getElementById('adminPass').value.trim()
        };

        try {
            const resp = await fetch(`${BASE}/clientes`, {
                method: 'POST', headers, body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (!resp.ok) throw new Error(data.error || 'Error al crear cliente');

            showToast('toastCliente',
                `Cliente creado. Empresa ID: ${data.empresa_id} | Usuario: ${data.username}`);
            document.getElementById('formCrearCliente').reset();

            // Actualizar el select de empresas para la tarjeta de sucursal
            await cargarEmpresas();
        } catch (err) {
            showToast('toastCliente', err.message, true);
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-circle me-1"></i> Generar Cliente y Cuenta';
        }
    });

    // ─── Formulario 2: Crear Sucursal ─────────────────────────────────────────
    document.getElementById('formCrearSucursal').addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = document.getElementById('btnCrearSucursal');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Creando...';

        const payload = {
            empresa_id: document.getElementById('empresaSelect').value,
            nombre_sucursal: document.getElementById('sucNombre').value.trim()
        };

        try {
            const resp = await fetch(`${BASE}/sucursales`, {
                method: 'POST', headers, body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (!resp.ok) throw new Error(data.error || 'Error al crear sucursal');

            showToast('toastSucursal',
                `Sucursal creada. ID: ${data.sucursal_id}`);
            document.getElementById('formCrearSucursal').reset();

            // Actualizar el select de sucursales para la tarjeta de sector
            await cargarSucursales();
        } catch (err) {
            showToast('toastSucursal', err.message, true);
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-plus-circle me-1"></i> Generar Sucursal';
        }
    });

    // ─── Formulario 3: Crear Sector ──────────────────────────────────────────
    document.getElementById('formCrearSector').addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = document.getElementById('btnCrearSector');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Creando...';

        const payload = {
            sucursal_id: document.getElementById('sucursalSelect').value,
            nombre_sector: document.getElementById('sectorNombre').value.trim()
        };

        try {
            const resp = await fetch(`${BASE}/sectores`, {
                method: 'POST', headers, body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (!resp.ok) throw new Error(data.error || 'Error al crear sector');

            showToast('toastSector',
                `Sector creado. ID: ${data.sector_id} en Sucursal ID: ${data.sucursal_id}`);
            document.getElementById('formCrearSector').reset();
        } catch (err) {
            showToast('toastSector', err.message, true);
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-node-plus-fill me-1"></i> Generar Sector';
        }
    });

    // ─── Agentes locales ─────────────────────────────────────────────────────
    const selAgEmpresa  = document.getElementById('agenteEmpresaSelect');
    const selAgSucursal = document.getElementById('agenteSucursalSelect');

    /** Sucursales de la empresa elegida (el endpoint devuelve el nombre de la empresa, que es único). */
    selAgEmpresa.addEventListener('change', () => {
        const empresa = empresasCache.find(e => String(e.id) === selAgEmpresa.value);
        selAgSucursal.innerHTML = '';
        const todas = document.createElement('option');
        todas.value = '';
        todas.textContent = 'Todas las de la empresa';
        selAgSucursal.appendChild(todas);
        if (!empresa) return;
        sucursalesCache.filter(s => s.empresa === empresa.nombre).forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.id;
            opt.textContent = s.nombre;
            selAgSucursal.appendChild(opt);
        });
    });

    function mostrarClave(clave) {
        document.getElementById('claveAgenteValor').value = clave;
        document.getElementById('claveAgenteBox').classList.remove('d-none');
    }

    document.getElementById('btnCopiarClave').addEventListener('click', async () => {
        const input = document.getElementById('claveAgenteValor');
        try {
            await navigator.clipboard.writeText(input.value);
        } catch {
            input.select();
            document.execCommand('copy');
        }
        showToast('toastAgente', 'Clave copiada al portapapeles.');
    });

    function celda(texto, clase) {
        const td = document.createElement('td');
        if (clase) td.className = clase;
        td.textContent = texto ?? '';
        return td;
    }

    function boton(texto, clase, onClick) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = `btn btn-sm ${clase} ms-1`;
        b.textContent = texto;
        b.addEventListener('click', onClick);
        return b;
    }

    async function cargarAgentes() {
        const tbody = document.getElementById('tablaAgentes');
        try {
            const resp = await fetch(`${BASE}/agentes`, { headers });
            if (!resp.ok) throw new Error('No se pudo cargar la lista de agentes');
            const agentes = await resp.json();
            tbody.innerHTML = '';
            if (agentes.length === 0) {
                const tr = document.createElement('tr');
                tr.appendChild(celda('Todavía no hay agentes.', 'text-muted text-center'));
                tr.firstChild.colSpan = 6;
                tbody.appendChild(tr);
                return;
            }
            agentes.forEach(a => {
                const tr = document.createElement('tr');

                const tdEstado = document.createElement('td');
                const badge = document.createElement('span');
                if (!a.activo)        { badge.className = 'badge bg-secondary'; badge.textContent = 'Revocado'; }
                else if (a.enLinea)   { badge.className = 'badge bg-success';   badge.textContent = 'En línea'; }
                else if (!a.ultimaConexion) { badge.className = 'badge bg-warning text-dark'; badge.textContent = 'Nunca conectó'; }
                else                  { badge.className = 'badge bg-danger';    badge.textContent = 'Sin conexión'; }
                tdEstado.appendChild(badge);
                tr.appendChild(tdEstado);

                tr.appendChild(celda(a.nombre + (a.version ? ` (v${a.version})` : ''), 'fw-bold'));
                tr.appendChild(celda(`${a.empresa} / ${a.sucursal}`));
                tr.appendChild(celda(a.ultimaConexion ? a.ultimaConexion.replace('T', ' ').substring(0, 19) : '-'));
                tr.appendChild(celda(a.estado || '-', 'small text-muted'));

                const tdAcc = document.createElement('td');
                tdAcc.className = 'text-end text-nowrap';
                tdAcc.appendChild(boton('Nueva clave', 'btn-outline-warning', async () => {
                    if (!confirm(`¿Generar una clave nueva para "${a.nombre}"? La actual deja de funcionar.`)) return;
                    const r = await fetch(`${BASE}/agentes/${a.id}/regenerar-clave`, { method: 'POST', headers });
                    const data = await r.json();
                    if (!r.ok) { showToast('toastAgente', data.error || 'Error', true); return; }
                    mostrarClave(data.clave);
                    await cargarAgentes();
                }));
                if (a.activo) {
                    tdAcc.appendChild(boton('Revocar', 'btn-outline-danger', async () => {
                        if (!confirm(`¿Revocar "${a.nombre}"? Deja de poder subir fichadas.`)) return;
                        const r = await fetch(`${BASE}/agentes/${a.id}/revocar`, { method: 'POST', headers });
                        const data = await r.json();
                        showToast('toastAgente', data.message || data.error, !r.ok);
                        await cargarAgentes();
                    }));
                }
                tr.appendChild(tdAcc);
                tbody.appendChild(tr);
            });
        } catch (err) {
            tbody.innerHTML = '';
            const tr = document.createElement('tr');
            tr.appendChild(celda(err.message, 'text-danger text-center'));
            tr.firstChild.colSpan = 6;
            tbody.appendChild(tr);
        }
    }

    document.getElementById('formCrearAgente').addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = document.getElementById('btnCrearAgente');
        btn.disabled = true;
        const payload = {
            empresa_id: selAgEmpresa.value,
            sucursal_id: selAgSucursal.value || null,
            nombre: document.getElementById('agenteNombre').value.trim()
        };
        try {
            const resp = await fetch(`${BASE}/agentes`, { method: 'POST', headers, body: JSON.stringify(payload) });
            const data = await resp.json();
            if (!resp.ok) throw new Error(data.error || 'Error al crear el agente');
            mostrarClave(data.clave);
            document.getElementById('agenteNombre').value = '';
            await cargarAgentes();
        } catch (err) {
            showToast('toastAgente', err.message, true);
        } finally {
            btn.disabled = false;
        }
    });

    await cargarAgentes();
    setInterval(cargarAgentes, 30000);

});
