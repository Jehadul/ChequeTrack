package com.chequetrack.organization.repository;

import com.chequetrack.organization.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findAllByOrderBySortOrder();
}
