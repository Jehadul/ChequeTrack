package com.chequetrack.security.service;

public interface UserAccessProvider {

    UserAccess forRole(Long roleId);
}
