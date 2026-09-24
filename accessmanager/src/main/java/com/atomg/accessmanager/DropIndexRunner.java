package com.atomg.accessmanager;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DropIndexRunner implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            jdbcTemplate.execute("ALTER TABLE empleados DROP INDEX UK_legajo_reloj");
            System.out.println("============== INDICE UK_legajo_reloj ELIMINADO EXITOSAMENTE ==============");
        } catch (Exception e) {
            System.out.println("============== NO SE PUDO ELIMINAR EL INDICE (PUEDE QUE YA NO EXISTA) ==============");
            e.printStackTrace();
        }
    }
}
