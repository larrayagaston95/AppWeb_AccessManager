package com.atomg.accessmanager.config;

import com.atomg.accessmanager.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private com.atomg.accessmanager.security.AgenteAuthFilter agenteAuthFilter;

    /** Orígenes permitidos (CORS_ALLOWED_ORIGINS), separados por coma. "*" = cualquiera. */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Configuracion CORS global.
     * Permite el header Authorization en preflight OPTIONS para que el browser
     * pueda enviar el JWT en las llamadas GET/POST del panel SuperAdmin.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origenes = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(origenes.isEmpty() ? List.of("*") : origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false); // false porque usamos wildcard en origen

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/hardware/**", "/iclock/**")
                .disable()
            )
            // Usamos el bean CorsConfigurationSource definido arriba
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Endpoints públicos y Login FluxTech
                .requestMatchers("/api/auth/**").permitAll()
                // Hardware webhooks (Hikvision / ZKTeco ADMS) sin JWT
                .requestMatchers("/api/hardware/**", "/iclock/**").permitAll()
                // Fichadas corporativas: la validación se hace en el controlador (token de empresa / JWT)
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/fichadas").permitAll()
                // Endpoint de prueba de calculo: solo SuperAdmin
                .requestMatchers("/api/calculo/**").hasAuthority("ROLE_SUPERADMIN")
                // Preflight OPTIONS: siempre permitir sin token
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Agentes locales: autenticados con su clave (X-Agent-Key)
                .requestMatchers("/api/agente/**").hasAuthority("ROLE_AGENTE")
                // Panel SuperAdmin: solo ROLE_SUPERADMIN
                // hasAuthority("ROLE_SUPERADMIN") coincide con el valor exacto guardado en BD y en el JWT
                .requestMatchers("/api/superadmin/**").hasAuthority("ROLE_SUPERADMIN")
                // Resto de la API: autenticado
                .requestMatchers("/api/**").authenticated()
                // Estaticos y SPA: libre
                .anyRequest().permitAll()
            )
            .addFilterBefore(agenteAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}