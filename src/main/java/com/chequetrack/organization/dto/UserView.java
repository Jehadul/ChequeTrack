package com.chequetrack.organization.dto;

import java.time.LocalDateTime;
import java.util.List;

/** A user as shown on list and detail pages, with names instead of ids. */
public record UserView(Long id,
                       String username,
                       String fullName,
                       String email,
                       String roleName,
                       String homeBranch,
                       boolean allBranches,
                       List<String> branches,
                       boolean enabled,
                       boolean locked,
                       boolean mustChangePassword,
                       LocalDateTime lastLoginAt) {

    public String branchScope() {
        return allBranches ? "All branches" : String.join(", ", branches);
    }
}
