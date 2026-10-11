package com.chequetrack.security.web;

import com.chequetrack.security.service.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * While a user's password is temporary or expired, every page redirects to the
 * change-password page. Registered in {@link AccountWebConfig}.
 */
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    public static final String CHANGE_PASSWORD_URL = "/account/change-password";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal user && user.isPasswordChangeRequired()) {
            response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_URL);
            return false;
        }
        return true;
    }
}
