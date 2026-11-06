package com.chequetrack.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Recipients {

    public record Person(Long id, String name, String email) {
        public boolean hasEmail() {
            return email != null && !email.isBlank();
        }
    }

    public record BranchInfo(Long id, Long companyId, String code, String name, String email) {
        public String label() {
            return code + " - " + name;
        }
    }

    private final JdbcTemplate jdbc;

    public Recipients(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> companies() {
        return jdbc.queryForList("select id from company where deleted = false order by id", Long.class);
    }

    public List<BranchInfo> activeBranches(Long companyId) {
        return jdbc.query("select id, company_id, code, name, email from branch "
                        + "where company_id = ? and active and deleted = false order by code",
                (rs, i) -> new BranchInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                companyId);
    }

    public BranchInfo branch(Long branchId) {
        return jdbc.queryForObject("select id, company_id, code, name, email from branch where id = ?",
                (rs, i) -> new BranchInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                branchId);
    }

    /**
     * Active users whose role has one of the permissions and who work in the branch (home or assigned
     * branch). "All branches" users count only for their home branch, so an administrator is not sent
     * every branch's reminders.
     */
    public List<Person> branchStaff(Long companyId, Long branchId, String... permissions) {
        return jdbc.query("""
                        select distinct u.id, u.full_name, u.email from app_user u
                          join role_permission rp on rp.role_id = u.role_id
                         where u.company_id = ? and u.enabled and u.deleted = false
                           and rp.permission_code = any (?)
                           and (u.home_branch_id = ?
                                or (not u.all_branches and exists (select 1 from user_branch ub
                                                                    where ub.user_id = u.id and ub.branch_id = ?)))
                         order by u.full_name
                        """,
                (rs, i) -> new Person(rs.getLong(1), rs.getString(2), rs.getString(3)),
                companyId, permissions, branchId, branchId);
    }

    /** Users who may decide an approval step: the step's role, APPROVAL_DECIDE and access to the branch. */
    public List<Person> approvers(Long companyId, Long branchId, Long roleId) {
        return jdbc.query("""
                        select u.id, u.full_name, u.email from app_user u
                         where u.company_id = ? and u.enabled and u.deleted = false and u.role_id = ?
                           and exists (select 1 from role_permission rp
                                        where rp.role_id = u.role_id and rp.permission_code = 'APPROVAL_DECIDE')
                           and (u.all_branches or u.home_branch_id = ?
                                or exists (select 1 from user_branch ub where ub.user_id = u.id and ub.branch_id = ?))
                         order by u.full_name
                        """,
                (rs, i) -> new Person(rs.getLong(1), rs.getString(2), rs.getString(3)),
                companyId, roleId, branchId, branchId);
    }

    public Person user(Long userId) {
        return jdbc.queryForObject("select id, full_name, email from app_user where id = ?",
                (rs, i) -> new Person(rs.getLong(1), rs.getString(2), rs.getString(3)), userId);
    }
}
