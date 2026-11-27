
package com.projectaurora.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // Disable CSRF because this is a stateless REST API
                .csrf(AbstractHttpConfigurer::disable)

                // JWT authentication does not use server-side sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                // Return 401 when authentication is required but missing
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED)))

                // Authorization rules
                .authorizeHttpRequests(authorize -> authorize

                        // Public authentication endpoints
                        .requestMatchers("/auth/**")
                        .permitAll()

                        // Existing role-based test endpoints
                        .requestMatchers("/api/test/student")
                        .hasRole("STUDENT")

                        .requestMatchers("/api/test/instructor")
                        .hasRole("INSTRUCTOR")

                        .requestMatchers("/api/test/admin")
                        .hasRole("ADMIN")

                        // Course creation: INSTRUCTOR only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses",
                                "/api/courses/")
                        .hasRole("INSTRUCTOR")

                        // Instructor's own courses: INSTRUCTOR only
                        // Must appear before the general GET matcher
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/mine")
                        .hasRole("INSTRUCTOR")

                        // Browse courses and view individual courses
                        // Any authenticated user
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses",
                                "/api/courses/",
                                "/api/courses/**")
                        .authenticated()

                        // Update courses: INSTRUCTOR or ADMIN
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/courses/**")
                        .hasAnyRole("INSTRUCTOR", "ADMIN")

                        // Publish courses: INSTRUCTOR or ADMIN
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/courses/*/publish")
                        .hasAnyRole("INSTRUCTOR", "ADMIN")

                        // Everything else requires authentication
                        .anyRequest()
                        .authenticated()
                )

                // Authentication provider
                .authenticationProvider(authenticationProvider())

                // JWT authentication filter
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }
}
