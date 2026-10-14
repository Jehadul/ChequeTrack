package com.chequetrack.security.api;

import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API login: exchange username and password for a bearer token. */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;

    public AuthApiController(AuthenticationManager authenticationManager, JwtTokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        AppUserPrincipal user = (AppUserPrincipal) auth.getPrincipal();
        if (user.isPasswordChangeRequired()) {
            throw new CredentialsExpiredException("Password change required. Log in to the web application first.");
        }
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }

    /** Who am I? Useful to test a token. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getFullName());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record MeResponse(Long id, String username, String fullName) {
    }
}
