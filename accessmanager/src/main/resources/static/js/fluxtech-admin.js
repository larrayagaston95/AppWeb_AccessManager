/**
 * fluxtech-admin.js
 * Logica del panel SuperAdmin de FluxTech.
 * Gestiona: Alta de Clientes, Sucursales y Sectores.
 */

const BASE = 'http://localhost:8080/api/superadmin';

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

    // ─── Cargar empresas en el select de Alta Sucursal ───────────────────────
    async function cargarEmpresas() {
        try {
            const resp = await fetch(`${BASE}/empresas`, { headers });
            if (!resp.ok) throw new Error('No se pudo cargar la lista de empresas');
            const empresas = await resp.json();
            populateSelect('empresaSelect', empresas, e => `${e.nombre}  (ID: ${e.id})`);
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

});