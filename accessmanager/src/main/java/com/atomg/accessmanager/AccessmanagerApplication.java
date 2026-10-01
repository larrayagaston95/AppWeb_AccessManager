package com.atomg.accessmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class AccessmanagerApplication {

	public static void main(String[] args) {
		// CRÍTICO: debe ejecutarse ANTES de SpringApplication.run() para que
		// HikariCP y el driver JDBC de MySQL abran sus conexiones ya en UTC,
		// evitando que el SO aplique su offset antes de que Spring arranque.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(AccessmanagerApplication.class, args);
	}
}
