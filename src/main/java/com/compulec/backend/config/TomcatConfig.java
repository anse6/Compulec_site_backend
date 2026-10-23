package com.compulec.backend.config;

import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Tomcat : augmente la limite du nombre de parts multipart.
 * 
 * Tomcat 10+ limite par défaut à 10 parts par requête multipart (anti-DoS).
 * On lève cette limite à 500 pour permettre les formulaires avec beaucoup de champs.
 */
@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> containerCustomizer() {
        return factory -> factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector ->
            connector.setMaxPartCount(500)
        );
    }
}
