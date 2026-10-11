package com.chequetrack.security.service;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.security.config.SecurityProperties;
import com.chequetrack.security.domain.AppUser;
import com.chequetrack.security.domain.PasswordHistory;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Creating users and changing or resetting passwords. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final AppUserRepository userRepository;
    private final PasswordHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Creates a user with a temporary password that must be changed at first login. */
    @Transactional
    public AppUser createUser(String username, String fullName, String email, String temporaryPassword) {
        if (userRepository.existsByUsernameIgnoreCase(username.trim())) {
            throw new BusinessException("Username '" + username + "' is already taken.");
        }
        Instant now = clock.instant();
        AppUser user = AppUser.create(username, fullName, email, passwordEncoder.encode(temporaryPassword), now);
        userRepository.save(user);
        historyRepository.save(new PasswordHistory(user.getId(), user.getPasswordHash(), now));
        return user;
    }

    /**
     * The logged-in user changes their own password.
     * @throws BusinessException with every broken rule, one per line
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect.");
        }
        List<String> errors = passwordPolicy.validate(newPassword, user.getUsername());
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("\n", errors));
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash()) || isRecentlyUsed(userId, newPassword)) {
            throw new BusinessException("You cannot reuse one of your last "
                    + Math.max(1, properties.password().historyCount()) + " passwords.");
        }

        Instant now = clock.instant();
        String hash = passwordEncoder.encode(newPassword);
        user.changePassword(hash, now);
        historyRepository.save(new PasswordHistory(userId, hash, now));
    }

    /**
     * An administrator resets a user's password (screen added in M2).
     * @return the generated temporary password, to be given to the user
     */
    @Transactional
    public String resetPassword(Long userId) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        String temporary = generateTemporaryPassword();
        user.resetPassword(passwordEncoder.encode(temporary), clock.instant());
        return temporary;
    }

    /** Random password that satisfies the default policy: letters, digits and a special character. */
    public String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        sb.append('A').append('z').append(random.nextInt(10)).append('#');
        return sb.toString();
    }

    private boolean isRecentlyUsed(Long userId, String rawPassword) {
        int count = properties.password().historyCount();
        if (count == 0) {
            return false;
        }
        return historyRepository.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(count)).stream()
                .anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
    }
}
