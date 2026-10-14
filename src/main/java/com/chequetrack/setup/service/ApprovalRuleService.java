package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.organization.domain.Role;
import com.chequetrack.organization.repository.RoleRepository;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.ApprovalAction;
import com.chequetrack.setup.domain.ApprovalRule;
import com.chequetrack.setup.dto.ApprovalRuleForm;
import com.chequetrack.setup.repository.ApprovalRuleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Approval steps per action. {@link #requiredSteps} answers "who must approve
 * this action for this amount?" and is what M7 enforces.
 */
@Service
public class ApprovalRuleService {

    private static final String MANAGE = "hasAuthority('" + SetupPermissions.SETTINGS_MANAGE + "')";

    private final ApprovalRuleRepository ruleRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher events;

    public ApprovalRuleService(ApprovalRuleRepository ruleRepository,
                               RoleRepository roleRepository,
                               ApplicationEventPublisher events) {
        this.ruleRepository = ruleRepository;
        this.roleRepository = roleRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public Map<ApprovalAction, List<ApprovalRule>> allGrouped() {
        Map<ApprovalAction, List<ApprovalRule>> groups = new EnumMap<>(ApprovalAction.class);
        for (ApprovalAction action : ApprovalAction.values()) {
            groups.put(action, new ArrayList<>());
        }
        ruleRepository.findByCompanyIdOrderByActionAscStepOrderAsc(companyId())
                .forEach(r -> groups.get(r.getAction()).add(r));
        return groups;
    }

    /** Role names by id, for display. */
    @Transactional(readOnly = true)
    public Map<Long, String> roleNames() {
        return roleRepository.findByCompanyIdOrderByName(companyId()).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
    }

    /**
     * Active steps required for this action and amount, in approval order. Empty =
     * no approval needed.
     */
    @Transactional(readOnly = true)
    public List<RequiredStep> requiredSteps(Long companyId, ApprovalAction action, BigDecimal amount) {
        Map<Long, String> roles = roleRepository.findByCompanyIdOrderByName(companyId).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        return ruleRepository.findByCompanyIdAndActionOrderByStepOrderAsc(companyId, action).stream()
                .filter(r -> r.appliesTo(amount))
                .map(r -> new RequiredStep(r.getStepOrder(), r.getApproverRoleId(),
                        roles.getOrDefault(r.getApproverRoleId(), "?")))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRule get(Long id) {
        return ruleRepository.findByIdAndCompanyId(id, companyId())
                .orElseThrow(() -> new NotFoundException("Approval rule", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public ApprovalRuleForm editForm(Long id) {
        ApprovalRule r = get(id);
        ApprovalRuleForm form = new ApprovalRuleForm();
        form.setId(r.getId());
        form.setVersion(r.getVersion());
        form.setAction(r.getAction());
        form.setStepOrder(r.getStepOrder());
        form.setMinAmount(r.getMinAmount());
        form.setApproverRoleId(r.getApproverRoleId());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public ApprovalRule create(ApprovalRuleForm form) {
        validateRole(form.getApproverRoleId());
        if (ruleRepository.existsByCompanyIdAndActionAndStepOrder(companyId(), form.getAction(), form.getStepOrder())) {
            throw new BusinessException(
                    "Step " + form.getStepOrder() + " already exists for '" + form.getAction().getLabel() + "'.");
        }
        ApprovalRule rule = ApprovalRule.create(companyId(), form.getAction());
        rule.update(form.getStepOrder(), form.getMinAmount(), form.getApproverRoleId());
        ruleRepository.save(rule);
        events.publishEvent(AuditEvent.of("APPROVAL_RULE_CREATED", "ApprovalRule", rule.getId(),
                "Added approval step " + rule.getStepOrder() + " for '" + rule.getAction().getLabel() + "': "
                        + roleNames().get(rule.getApproverRoleId()) + " from " + rule.getMinAmount()));
        return rule;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, ApprovalRuleForm form) {
        ApprovalRule rule = get(id);
        rule.checkVersion(form.getVersion());
        validateRole(form.getApproverRoleId());
        if (rule.getStepOrder() != form.getStepOrder()
                && ruleRepository.existsByCompanyIdAndActionAndStepOrder(companyId(), rule.getAction(),
                        form.getStepOrder())) {
            throw new BusinessException(
                    "Step " + form.getStepOrder() + " already exists for '" + rule.getAction().getLabel() + "'.");
        }
        Map<Long, String> roles = roleNames();
        Changes changes = new Changes()
                .add("stepOrder", rule.getStepOrder(), form.getStepOrder())
                .add("minAmount", rule.getMinAmount().stripTrailingZeros().toPlainString(),
                        form.getMinAmount().stripTrailingZeros().toPlainString())
                .add("approverRole", roles.get(rule.getApproverRoleId()), roles.get(form.getApproverRoleId()));
        rule.update(form.getStepOrder(), form.getMinAmount(), form.getApproverRoleId());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("APPROVAL_RULE_UPDATED", "ApprovalRule", rule.getId(),
                    "Updated approval step for '" + rule.getAction().getLabel() + "'", changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void delete(Long id) {
        ApprovalRule rule = get(id);
        rule.markDeleted();
        events.publishEvent(AuditEvent.of("APPROVAL_RULE_DELETED", "ApprovalRule", rule.getId(),
                "Removed approval step " + rule.getStepOrder() + " for '" + rule.getAction().getLabel() + "'"));
    }

    private void validateRole(Long roleId) {
        roleRepository.findByIdAndCompanyId(roleId, companyId())
                .orElseThrow(() -> new BusinessException("Choose a valid approver role."));
    }

    private static Long companyId() {
        return CurrentUser.get().getCompanyId();
    }

    /** One approval needed: step number and the role that must approve. */
    public record RequiredStep(int stepOrder, Long roleId, String roleName) {
    }
}
