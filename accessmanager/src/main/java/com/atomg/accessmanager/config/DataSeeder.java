package com.atomg.accessmanager.config;

import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Usuario;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Sembrar usuario admin si no existe
        Optional<Usuario> adminOpt = usuarioRepository.findByUsername("admin");
        if (adminOpt.isEmpty()) {
            Empresa emp1 = new Empresa();
            emp1.setNombre("Cooperativa Test");
            try {
                emp1 = empresaRepository.save(emp1);
            } catch(Exception e) {
                // Si ya existe por nombre u otra restriccion
            }

            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRol("ROLE_ADMIN");
            admin.setEmpresa(emp1);
            
            usuarioRepository.save(admin);
            System.out.println("Usuario 'admin' creado con éxito");
        }

        // Sembrar usuario superadmin fluxtech
        Optional<Usuario> superOpt = usuarioRepository.findByUsername("fluxtech");
        if (superOpt.isEmpty()) {
            Empresa empFlux = new Empresa();
            empFlux.setNombre("FluxTech Admin");
            try {
                empFlux = empresaRepository.save(empFlux);
            } catch(Exception e) {
                // Ignorar si ya existía
            }

            Usuario superadmin = new Usuario();
            superadmin.setUsername("fluxtech");
            superadmin.setPassword(passwordEncoder.encode("fluxtech2026"));
            superadmin.setRol("ROLE_SUPERADMIN");
            superadmin.setEmpresa(empFlux);
            
            usuarioRepository.save(superadmin);
            System.out.println("Usuario maestro 'fluxtech' creado con éxito (SuperAdmin)");
        }
    }
}
