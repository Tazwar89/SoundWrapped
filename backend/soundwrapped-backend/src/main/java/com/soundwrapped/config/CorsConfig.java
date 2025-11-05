package com.soundwrapped.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000", "http://127.0.0.1:3001")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
        
        // Allow browser extension requests (Chrome extensions use chrome-extension:// origin, but also make requests directly)
        // Note: Cannot use allowCredentials(true) with wildcard origins - background scripts don't need credentials anyway
        registry.addMapping("/api/tracking/**")
                .allowedOriginPatterns("*") // Allow all origins for tracking endpoints (browser extensions)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false); // Background scripts don't send cookies
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Frontend CORS configuration (for non-tracking endpoints)
        CorsConfiguration frontendConfig = new CorsConfiguration();
        frontendConfig.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000", 
                "http://localhost:3001", 
                "http://127.0.0.1:3000", 
                "http://127.0.0.1:3001"
        ));
        frontendConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        frontendConfig.setAllowedHeaders(Arrays.asList("*"));
        frontendConfig.setAllowCredentials(true);
        
        // Browser extension CORS configuration (for tracking endpoints)
        CorsConfiguration extensionConfig = new CorsConfiguration();
        extensionConfig.setAllowedOriginPatterns(Arrays.asList("*"));
        extensionConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        extensionConfig.setAllowedHeaders(Arrays.asList("*"));
        extensionConfig.setAllowCredentials(false);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register tracking endpoints with extension config (higher priority due to more specific path)
        source.registerCorsConfiguration("/api/tracking/**", extensionConfig);
        // Register other API endpoints with frontend config
        source.registerCorsConfiguration("/api/**", frontendConfig);
        return source;
    }
}
