package com.associago.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Il {@link PasswordEncoder} vive fuori dalle configurazioni di sicurezza
 * legate a un profilo.
 *
 * <p>Stava dentro {@code SecurityConfig}, che vale solo fuori dal profilo
 * {@code cloud}: sul web il bean spariva e {@code AssociationService}, che lo
 * inietta per confrontare le password, impediva l'avvio dell'applicazione.
 * Serve in ogni profilo, quindi sta per conto suo.
 *
 * @since 0.2.0
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
