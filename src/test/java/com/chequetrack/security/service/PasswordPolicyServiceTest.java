package com.chequetrack.security.service;

import com.chequetrack.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyServiceTest {

    private final PasswordPolicyService policy = new PasswordPolicyService(new SecurityProperties(
            new SecurityProperties.Password(8, true, true, true, true, 3, 90),
            new SecurityProperties.Lockout(5, 15),
            new SecurityProperties.Jwt("x", "chequetrack", 60),
            new SecurityProperties.Bootstrap("admin", "")));

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.validate("Cheque#2026", "cashier1")).isEmpty();
    }

    @Test
    void rejectsShortPassword() {
        assertThat(policy.validate("Ab1#", "cashier1")).singleElement().asString().contains("at least 8");
    }

    @Test
    void reportsEveryMissingCharacterClass() {
        assertThat(policy.validate("alllowercase", "cashier1"))
                .hasSize(3)
                .anyMatch(e -> e.contains("uppercase"))
                .anyMatch(e -> e.contains("digit"))
                .anyMatch(e -> e.contains("special"));
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThat(policy.validate("Cashier1#Pass", "cashier1")).anyMatch(e -> e.contains("username"));
    }

    @Test
    void rejectsSpaces() {
        assertThat(policy.validate("Cheque #2026", "cashier1")).anyMatch(e -> e.contains("spaces"));
    }

    @Test
    void temporaryPasswordsSatisfyThePolicy() {
        AccountService accounts = new AccountService(null, null, null, policy, null, null, null);
        for (int i = 0; i < 50; i++) {
            assertThat(policy.validate(accounts.generateTemporaryPassword(), "admin")).isEmpty();
        }
    }
}
