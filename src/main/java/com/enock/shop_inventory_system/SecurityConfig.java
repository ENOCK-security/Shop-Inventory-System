package com.enock.shop_inventory_system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        AuthenticationSuccessHandler loginSuccessHandler =
                (request, response, authentication) -> {
                    response.setStatus(200);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"authenticated\":true,\"role\":\"ADMIN\"}"
                    );
                };

        AuthenticationFailureHandler loginFailureHandler =
                (request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"authenticated\":false,\"message\":\"Invalid administrator credentials\"}"
                    );
                };

        LogoutSuccessHandler logoutSuccessHandler =
                (request, response, authentication) -> {
                    response.setStatus(200);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"authenticated\":false}"
                    );
                };

        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/style.css",
                    "/app.js",
                    "/api/health",
                    "/api/auth/csrf",
                    "/api/auth/login"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/products",
                    "/api/products/",
                    "/api/products/*",
                    "/api/products/sku/**"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/payments/simulate/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/auth-check").hasRole("ADMIN")
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        "{\"authenticated\":false,\"message\":\"Authentication is required\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        "{\"message\":\"Access denied or CSRF token is invalid\"}");
                })
            )
            .formLogin(form -> form
                .loginPage("/")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(logoutSuccessHandler)
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin.username}") String username,
            @Value("${app.security.admin.password}") String password,
            PasswordEncoder passwordEncoder) {

        UserDetails administrator = User
            .withUsername(username)
            .password(passwordEncoder.encode(password))
            .roles("ADMIN")
            .build();

        return new InMemoryUserDetailsManager(administrator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}