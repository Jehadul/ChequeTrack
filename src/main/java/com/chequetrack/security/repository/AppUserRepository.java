package com.chequetrack.security.repository;

import com.chequetrack.security.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u where lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsername(@Param("username") String username);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Counts a failed login in one atomic statement. When the limit is reached
     * the account is locked until {@code lockUntil} and the counter restarts.
     * PostgreSQL evaluates every SET expression with the old row values.
     */
    @Modifying
    @Query(value = """
            UPDATE app_user
               SET failed_login_attempts = CASE WHEN failed_login_attempts + 1 >= :maxAttempts
                                                THEN 0 ELSE failed_login_attempts + 1 END,
                   locked_until          = CASE WHEN failed_login_attempts + 1 >= :maxAttempts
                                                THEN :lockUntil ELSE locked_until END
             WHERE lower(username) = lower(:username) AND deleted = FALSE
            """, nativeQuery = true)
    int registerFailedLogin(@Param("username") String username,
                            @Param("maxAttempts") int maxAttempts,
                            @Param("lockUntil") Instant lockUntil);

    @Modifying
    @Query(value = """
            UPDATE app_user
               SET failed_login_attempts = 0, locked_until = NULL, last_login_at = :now
             WHERE id = :userId
            """, nativeQuery = true)
    int registerSuccessfulLogin(@Param("userId") Long userId, @Param("now") Instant now);
}
