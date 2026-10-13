package com.chequetrack.security.config;

import com.chequetrack.security.service.AppUserDetailsService;
import com.chequetrack.security.service.JwtUserAuthenticationConverter;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.web.LoginFailureHandler;
import com.chequetrack.security.web.SessionRefreshFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Two filter chains (ARCHITECTURE.md section 5):
 * <ol>
 *   <li>{@code /api/**}: stateless, JWT bearer token, no CSRF.</li>
 *   <li>Everything else: login form, HTTP session, CSRF protection.</li>
 * </ol>
 * The login form and the API login ({@code POST /api/auth/login}) share one
 * AuthenticationManager, so lockout rules apply to both.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_RESOURCES = {
            "/login", "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/error",
            "/actuator/health"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder,
                                                       AuthenticationEventPublisher eventPublisher) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        ProviderManager manager = new ProviderManager(provider);
        manager.setAuthenticationEventPublisher(eventPublisher);
        return manager;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              JwtUserAuthenticationConverter jwtConverter) throws Exception {
        // No password AuthenticationManager here: bearer tokens are checked by the resource
        // server's own JWT provider. /api/auth/login uses the shared manager directly.
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http,
                                              AuthenticationManager authenticationManager,
                                              LoginFailureHandler loginFailureHandler,
                                              AppUserRepository userRepository,
                                              AppUserDetailsService userDetailsService,
                                              ApplicationEventPublisher events) throws Exception {
        http.authenticationManager(authenticationManager)
                // Before authorization, so refreshed permissions are the ones checked
                .addFilterBefore(new SessionRefreshFilter(userRepository, userDetailsService, events), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_RESOURCES).permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(s -> s.sessionFixation(fixation -> fixation.changeSessionId()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'")));
        return http.build();
    }
}
