package com.atomg.accessmanager.config;

import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Usuario;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Crea los usuarios iniciales solo si no existen.
 * Las contraseñas salen de SEED_ADMIN_PASSWORD y SEED_SUPERADMIN_PASSWORD; si no están
 * configuradas se genera una aleatoria y se muestra una única vez en el log de arranque.
 * No afecta a usuarios ya existentes: esos hay que rotarlos a mano.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${SEED_ADMIN_PASSWORD:}")
    private String seedAdminPassword;

    @Value("${SEED_SUPERADMIN_PASSWORD:}")
    private String seedSuperadminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (usuarioRepository.count() == 0) {
            Empresa emp1 = obtenerOCrearEmpresa("Cooperativa Test");

            String pass = elegirPassword(seedAdminPassword, "admin");
            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(pass));
            admin.setRol("ROLE_ADMIN");
            admin.setEmpresa(emp1);
            usuarioRepository.save(admin);
            log.info("Usuario 'admin' creado.");
        }

        Optional<Usuario> superOpt = usuarioRepository.findByUsername("fluxtech");
        if (superOpt.isEmpty()) {
            Empresa empFlux = obtenerOCrearEmpresa("FluxTech Admin");

            String pass = elegirPassword(seedSuperadminPassword, "fluxtech");
            Usuario superadmin = new Usuario();
            superadmin.setUsername("fluxtech");
            superadmin.setPassword(passwordEncoder.encode(pass));
            superadmin.setRol("ROLE_SUPERADMIN");
            superadmin.setEmpresa(empFlux);
            usuarioRepository.save(superadmin);
            log.info("Usuario maestro 'fluxtech' creado (SuperAdmin).");
        }
    }

    private Empresa obtenerOCrearEmpresa(String nombre) {
        return empresaRepository.findByNombre(nombre).orElseGet(() -> {
            Empresa e = new Empresa();
            e.setNombre(nombre);
            return empresaRepository.save(e);
        });
    }

    private String elegirPassword(String configurada, String usuario) {
        if (configurada != null && !configurada.isBlank()) {
            return configurada;
        }
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        String generada = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.warn("Contraseña inicial generada para '{}': {}  (se muestra una sola vez; cambiarla tras el primer ingreso)",
                usuario, generada);
        return generada;
    }
}
