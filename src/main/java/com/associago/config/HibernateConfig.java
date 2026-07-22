package com.associago.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Selezione dinamica del dialetto Hibernate per la modalità desktop (SQLite/MariaDB).
 * Nel profilo "cloud" è disattivata: dialetto e ddl-auto vengono da spring.jpa.* (MySQL).
 */
@Configuration
@Profile("!cloud")
public class HibernateConfig {

    private final AppConfigManager configManager;

    public HibernateConfig(AppConfigManager configManager) {
        this.configManager = configManager;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (properties) -> {
            AppConfig config = configManager.loadConfig();
            
            if (!config.isConfigured()) {
                // Setup Mode: SQLite
                properties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
                properties.put("hibernate.hbm2ddl.auto", "update"); 
            } else if ("SQLITE".equalsIgnoreCase(config.getDbType())) {
                properties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
                properties.put("hibernate.hbm2ddl.auto", "none"); 
            } else {
                // MariaDB
                properties.put("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
                properties.put("hibernate.hbm2ddl.auto", "validate");
            }
        };
    }
}
