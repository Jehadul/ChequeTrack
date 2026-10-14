package com.chequetrack.security.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.security.service.AccountService;
import com.chequetrack.security.service.AppUserDetailsService;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.PasswordPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The logged-in user's own account. */
@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final AppUserDetailsService userDetailsService;
    private final PasswordPolicyService passwordPolicy;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AccountController(AccountService accountService,
                             AppUserDetailsService userDetailsService,
                             PasswordPolicyService passwordPolicy) {
        this.accountService = accountService;
        this.userDetailsService = userDetailsService;
        this.passwordPolicy = passwordPolicy;
    }

    @ModelAttribute("passwordRules")
    public String passwordRules() {
        return passwordPolicy.describe();
    }

    @GetMapping("/change-password")
    public String changePasswordForm(@AuthenticationPrincipal AppUserPrincipal user, Model model) {
        model.addAttribute("form", new ChangePasswordForm());
        model.addAttribute("forced", user.isPasswordChangeRequired());
        return "account/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal user,
                                 @Valid @ModelAttribute("form") ChangePasswordForm form,
                                 BindingResult binding,
                                 Model model,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirect) {
        model.addAttribute("forced", user.isPasswordChangeRequired());
        if (binding.hasErrors()) {
            return "account/change-password";
        }
        try {
            accountService.changePassword(user.getId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessException ex) {
            binding.reject("password", ex.getMessage());
            return "account/change-password";
        }
        refreshLoggedInUser(user.getUsername(), request, response);
        redirect.addFlashAttribute("successMessage", "Your password has been changed.");
        return "redirect:/";
    }

    /** Replace the session's user snapshot so the "must change password" flag is cleared. */
    private void refreshLoggedInUser(String username, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal fresh = userDetailsService.loadUserByUsername(username);
        fresh.eraseCredentials();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
