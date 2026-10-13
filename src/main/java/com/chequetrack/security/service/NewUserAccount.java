package com.chequetrack.security.service;

import java.util.Set;

/** Everything needed to create a user. */
public record NewUserAccount(String username,
                             String fullName,
                             String email,
                             String temporaryPassword,
                             Long companyId,
                             Long roleId,
                             Long homeBranchId,
                             boolean allBranches,
                             Set<Long> branchIds) {
}
