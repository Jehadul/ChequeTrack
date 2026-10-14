package com.chequetrack.audit.service;

import com.chequetrack.audit.AuditPermissions;
import com.chequetrack.audit.domain.AuditLogEntry;
import com.chequetrack.audit.domain.StatusHistoryEntry;
import com.chequetrack.audit.repository.AuditLogRepository;
import com.chequetrack.audit.repository.StatusHistoryRepository;
import com.chequetrack.security.service.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Read-only access to the audit log and status history. */
@Service
public class AuditQueryService {

    public static final int PAGE_SIZE = 50;

    private final AuditLogRepository auditLogRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Clock clock;

    public AuditQueryService(AuditLogRepository auditLogRepository,
                             StatusHistoryRepository statusHistoryRepository,
                             Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.clock = clock;
    }

    /** Audit log of the user's company, newest first. Failed logins with unknown usernames are included. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + AuditPermissions.AUDIT_VIEW + "')")
    public Page<AuditLogEntry> search(AuditFilter filter, int page) {
        Long companyId = CurrentUser.get().getCompanyId();
        Specification<AuditLogEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.or(cb.equal(root.get("companyId"), companyId), cb.isNull(root.get("companyId"))));
            if (filter.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"),
                        filter.from().atStartOfDay(clock.getZone()).toInstant()));
            }
            if (filter.to() != null) {
                p.add(cb.lessThan(root.get("occurredAt"),
                        filter.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
            }
            if (notBlank(filter.username())) {
                p.add(cb.like(cb.lower(root.get("username")), "%" + escapeLike(filter.username().trim().toLowerCase()) + "%", '\\'));
            }
            if (notBlank(filter.action())) {
                p.add(cb.equal(root.get("action"), filter.action()));
            }
            if (notBlank(filter.entityType())) {
                p.add(cb.equal(root.get("entityType"), filter.entityType()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return auditLogRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return auditLogRepository.findActions(CurrentUser.get().getCompanyId());
    }

    @Transactional(readOnly = true)
    public List<String> entityTypes() {
        return auditLogRepository.findEntityTypes(CurrentUser.get().getCompanyId());
    }

    /**
     * Recent audit entries of one record. Callers must already have checked the
     * user may see that record (e.g. through the record's own service).
     */
    @Transactional(readOnly = true)
    public List<AuditLogEntry> recentForEntity(String entityType, Object entityId, int max) {
        return auditLogRepository.findByCompanyIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
                CurrentUser.get().getCompanyId(), entityType, String.valueOf(entityId), Limit.of(max));
    }

    /** Full status history of one record, oldest first. Same access rule as above. */
    @Transactional(readOnly = true)
    public List<StatusHistoryEntry> statusHistory(String entityType, Long entityId) {
        return statusHistoryRepository.findByCompanyIdAndEntityTypeAndEntityIdOrderByChangedAtAscIdAsc(
                CurrentUser.get().getCompanyId(), entityType, entityId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** User input is a plain substring, not a pattern. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record AuditFilter(LocalDate from, LocalDate to, String username, String action, String entityType) {
    }
}
