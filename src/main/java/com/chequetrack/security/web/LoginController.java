package com.chequetrack.security.web;

import com.chequetrack.security.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Shows the login form; Spring Security processes the POST. */
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final SecurityProperties properties;

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        Authentication authentication,
                        Model model) {
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.isAuthenticated()) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute("loginError", switch (error) {
                case "locked" -> "Your account is locked after too many failed attempts. Try again in "
                        + properties.lockout().lockMinutes() + " minutes or contact the administrator.";
                case "disabled" -> "Your account is disabled. Contact the administrator.";
                default -> "Invalid username or password.";
            });
        }
        if (logout != null) {
            model.addAttribute("loginInfo", "You have been logged out.");
        }
        return "login";
    }
}
