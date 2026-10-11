package com.chequetrack.security.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AccountWebConfig implements WebMvcConfigurer {

    private final PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeRequiredInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PasswordChangeRequiredInterceptor.CHANGE_PASSWORD_URL,
                        "/login", "/logout", "/error", "/api/**",
                        "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico");
    }
}
