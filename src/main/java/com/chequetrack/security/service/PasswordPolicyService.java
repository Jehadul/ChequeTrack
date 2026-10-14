package com.chequetrack.security.service;

import com.chequetrack.security.config.SecurityProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Checks a new password against the configured strength rules. */
@Service
public class PasswordPolicyService {

    private final SecurityProperties properties;

    public PasswordPolicyService(SecurityProperties properties) {
        this.properties = properties;
    }

    /** @return the broken rules, empty when the password is acceptable */
    public List<String> validate(String password, String username) {
        SecurityProperties.Password rules = properties.password();
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < rules.minLength()) {
            errors.add("Password must be at least " + rules.minLength() + " characters long.");
            return errors;
        }
        if (rules.requireUppercase() && password.chars().noneMatch(Character::isUpperCase)) {
            errors.add("Password must contain an uppercase letter.");
        }
        if (rules.requireLowercase() && password.chars().noneMatch(Character::isLowerCase)) {
            errors.add("Password must contain a lowercase letter.");
        }
        if (rules.requireDigit() && password.chars().noneMatch(Character::isDigit)) {
            errors.add("Password must contain a digit.");
        }
        if (rules.requireSpecial() && password.chars().allMatch(Character::isLetterOrDigit)) {
            errors.add("Password must contain a special character (e.g. @ # $ !).");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            errors.add("Password must not contain spaces.");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase().contains(username.trim().toLowerCase())) {
            errors.add("Password must not contain the username.");
        }
        return errors;
    }

    /** Human-readable summary shown next to password fields. */
    public String describe() {
        SecurityProperties.Password rules = properties.password();
        List<String> parts = new ArrayList<>();
        parts.add("at least " + rules.minLength() + " characters");
        if (rules.requireUppercase()) parts.add("an uppercase letter");
        if (rules.requireLowercase()) parts.add("a lowercase letter");
        if (rules.requireDigit()) parts.add("a digit");
        if (rules.requireSpecial()) parts.add("a special character");
        String text = "Use " + String.join(", ", parts) + ".";
        if (rules.historyCount() > 0) {
            text += " You cannot reuse your last " + rules.historyCount() + " passwords.";
        }
        return text;
    }
}
