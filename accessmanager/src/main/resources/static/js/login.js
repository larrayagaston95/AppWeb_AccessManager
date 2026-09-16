// Lógica de inicio de sesión con JWT

document.addEventListener('DOMContentLoaded', () => {
    // Si ya hay token, lo mandamos directo al dashboard
    if (localStorage.getItem('access_token_am')) {
        window.location.href = 'index.html';
        return;
    }

    const formLogin = document.getElementById('formLogin');
    const btnIngresar = document.getElementById('btnIngresar');
    const errorBox = document.getElementById('loginError');
    const errorMsg = document.getElementById('loginErrorMsg');

    formLogin.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const username = document.getElementById('inputUsername').value.trim();
        const password = document.getElementById('inputPassword').value.trim();

        if (!username || !password) return;

        btnIngresar.disabled = true;
        btnIngresar.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span> Validando...';
        errorBox.classList.add('d-none');

        try {
            const response = await fetch('http://localhost:8080/api/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ username, password })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || 'Credenciales incorrectas');
            }

            // Éxito: Guardar token JWT y datos de sesión
            localStorage.setItem('access_token_am', data.token);
            localStorage.setItem('username_am', data.username);
            localStorage.setItem('rol_am', data.rol);
            localStorage.setItem('empresa_id_am', data.empresaId);

            // Redirigir según el rol
            if (data.rol === 'ROLE_SUPERADMIN') {
                window.location.href = 'fluxtech-admin.html';
            } else {
                window.location.href = 'index.html';
            }

        } catch (error) {
            console.error('Error de autenticación:', error);
            errorMsg.innerText = error.message;
            errorBox.classList.remove('d-none');
        } finally {
            btnIngresar.disabled = false;
            btnIngresar.innerHTML = '<i class="bi bi-box-arrow-in-right me-1"></i> Iniciar Sesión';
        }
    });
});
