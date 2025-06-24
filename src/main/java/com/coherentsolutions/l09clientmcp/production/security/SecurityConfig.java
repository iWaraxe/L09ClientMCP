package com.coherentsolutions.l09clientmcp.production.security;

import com.coherentsolutions.l09clientmcp.production.config.ProductionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security Configuration for Production MCP Client.
 * 
 * This configuration provides comprehensive security setup including JWT authentication,
 * RBAC authorization, CORS handling, and environment-specific security policies.
 * 
 * Educational Focus:
 * - Spring Security configuration patterns
 * - JWT-based stateless authentication
 * - Role-based authorization rules
 * - Production security best practices
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    
    private final ProductionConfig config;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    /**
     * Main security filter chain for API endpoints.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring API security filter chain for production environment: {}", 
                config.getEnvironment());
        
        http
            .securityMatcher("/api/**", "/mcp/**")
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> {
                // Public endpoints
                authz.requestMatchers(HttpMethod.GET, "/api/chat/health").permitAll();
                
                // Authentication endpoints
                authz.requestMatchers("/auth/**").permitAll();
                
                // Chat endpoints - require authentication
                authz.requestMatchers("/api/chat/**").authenticated();
                
                // Production monitoring endpoints - role-based access
                authz.requestMatchers(HttpMethod.GET, "/api/production/status").hasAnyRole("ADMIN", "OPERATOR");
                authz.requestMatchers(HttpMethod.GET, "/api/production/metrics").hasAnyRole("ADMIN", "OPERATOR", "DEVELOPER");
                authz.requestMatchers(HttpMethod.GET, "/api/production/config").hasRole("ADMIN");
                authz.requestMatchers(HttpMethod.POST, "/api/production/**").hasRole("ADMIN");
                
                // MCP endpoints - authenticated users only
                authz.requestMatchers("/mcp/**").authenticated();
                
                // All other API endpoints require authentication
                authz.anyRequest().authenticated();
            })
            .exceptionHandling(exceptions -> 
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * Security filter chain for actuator endpoints.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> {
                if (config.isDevelopment()) {
                    // More permissive in development
                    authz.requestMatchers("/actuator/health/**").permitAll();
                    authz.requestMatchers("/actuator/info").permitAll();
                    authz.requestMatchers("/actuator/**").hasAnyRole("ADMIN", "DEVELOPER");
                } else {
                    // Restrictive in production
                    authz.requestMatchers("/actuator/health").permitAll();
                    authz.requestMatchers("/actuator/**").hasRole("ADMIN");
                }
            })
            .exceptionHandling(exceptions -> 
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * Default security filter chain for remaining endpoints.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> {
                // Error handling
                authz.requestMatchers("/error").permitAll();
                
                // Development-specific endpoints
                if (config.isDevelopment()) {
                    authz.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                }
                
                // All other requests require authentication
                authz.anyRequest().authenticated();
            })
            .exceptionHandling(exceptions -> 
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * CORS configuration for cross-origin requests.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        if (config.isDevelopment()) {
            // Permissive CORS for development
            configuration.setAllowedOriginPatterns(List.of("*"));
            configuration.setAllowCredentials(true);
        } else {
            // Restrictive CORS for production
            List<String> allowedOrigins = config.getSecurity().getCors().getAllowedOrigins();
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowCredentials(config.getSecurity().getCors().getAllowCredentials());
        }
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        configuration.setMaxAge(3600L); // 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        log.info("CORS configuration initialized for environment: {}", config.getEnvironment());
        
        return source;
    }
    
    /**
     * Password encoder for user authentication.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strong hashing
    }
    
    /**
     * JWT authentication entry point for handling unauthorized requests.
     */
    @Bean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }
}