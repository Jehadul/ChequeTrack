package com.chequetrack.notification.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.domain.ApprovalStatus;
import com.chequetrack.lifecycle.domain.ApprovalStep;
import com.chequetrack.lifecycle.domain.StepStatus;
import com.chequetrack.lifecycle.repository.ApprovalRequestRepository;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.setup.service.SettingsService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final ApprovalRequestRepository requestRepository;
    private final Recipients recipients;
    private final MailRenderer renderer;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final DisplayFormat format;
    private final EntityManager em;

    public AlertService(ApprovalRequestRepository requestRepository, Recipients recipients, MailRenderer renderer,
                        NotificationService notificationService, SettingsService settingsService, DisplayFormat format,
                        EntityManager em) {
        this.requestRepository = requestRepository;
        this.recipients = recipients;
        this.renderer = renderer;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.format = format;
        this.em = em;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvent(AuditEvent event) {
        try {
            switch (event.action()) {
                case "CHEQUE_BOUNCE" -> bounced(event);
                case "APPROVAL_REQUESTED", "APPROVAL_GRANTED", "APPROVAL_REJECTED" -> approval(event);
                default -> {
                    // not an alert
                }
            }
        } catch (RuntimeException ex) {
            log.error("Could not send the alert for {} {} {}", event.action(), event.entityType(), event.entityId(), ex);
        }
    }

    private void bounced(AuditEvent event) {
        if (!ReceivedCheque.RECORD_TYPE.equals(event.entityType())) {
            return;
        }
        ReceivedCheque c = em.find(ReceivedCheque.class, toLong(event.entityId()));
        if (c == null) {
            return;
        }
        int noticeDays = settingsService.forCompany(c.getCompanyId()).getBounceNoticeDays();
        List<String> lines = new ArrayList<>();
        lines.add("Customer: " + c.getParty().getName());
        lines.add("Amount: Tk " + format.amount(c.getAmount()) + ", cheque dated " + format.date(c.getChequeDate()));
        lines.add("Reason: " + c.getLastBounceReason());
        if (c.getLastBounceDate() != null) {
            lines.add("Send the legal notice (NI Act 1881, s.138) by " + format.date(c.getLastBounceDate().plusDays(noticeDays)) + ".");
        }
        lines.add("Next step: present it again, ask for a replacement, or return it.");
        String title = "Cheque " + c.getChequeNumber() + " of " + c.getParty().getName() + " bounced";
        String link = "/received/" + c.getId();
        NotificationService.Message m = new NotificationService.Message(c.getCompanyId(), c.getBranchId(), "BOUNCE", title,
                String.join("\n", lines), link, "Bounced: " + title.replace(" bounced", ""),
                renderer.alert(title, lines, link, "Open the cheque"), "BOUNCE:" + c.getId() + ":" + c.getBounceCount());
        notificationService.deliver(m, recipients.branchStaff(c.getCompanyId(), c.getBranchId(), ReceivedPermissions.RECEIVED_MANAGE),
                recipients.branch(c.getBranchId()).email());
    }

    private void approval(AuditEvent event) {
        List<ApprovalRequest> requests = requestRepository.findByRecordTypeAndRecordIdOrderByRequestedAtDesc(
                event.entityType(), toLong(event.entityId()));
        if (requests.isEmpty()) {
            return;
        }
        ApprovalRequest r = requests.get(0);
        List<String> lines = new ArrayList<>();
        lines.add(r.getApprovalAction().getLabel() + ": " + r.getRecordLabel());
        if (r.getAmount() != null) {
            lines.add("Amount: Tk " + format.amount(r.getAmount()));
        }
        if (r.getReason() != null) {
            lines.add("Reason given: " + r.getReason());
        }
        lines.add("Asked by " + r.getRequestedBy() + " on " + format.ts(r.getRequestedAt()));
        String link = "/approvals/" + r.getId();

        if (r.getStatus() == ApprovalStatus.PENDING) {
            ApprovalStep step = r.getWaitingStep().orElse(null);
            if (step == null) {
                return;
            }
            List<Recipients.Person> people = recipients.approvers(r.getCompanyId(), r.getBranchId(), step.getRoleId()).stream()
                    .filter(p -> !p.id().equals(r.getRequestedById()) && !r.decidedBy(p.id()))
                    .toList();
            String title = "Approval needed: " + r.getApprovalAction().getLabel().toLowerCase() + " – " + r.getRecordLabel();
            lines.add("Waiting for step " + step.getStepOrder() + " of " + r.getSteps().size() + " (" + step.getRoleName() + ").");
            send(r, "APPROVAL_REQUEST", title, lines, link, "Open the request", people,
                    "APPROVAL:" + r.getId() + ":STEP" + step.getStepOrder());
        } else if (r.getStatus() == ApprovalStatus.APPROVED && "APPROVAL_GRANTED".equals(event.action())) {
            String title = "Approved: " + r.getApprovalAction().getLabel().toLowerCase() + " – " + r.getRecordLabel();
            lines.add("The change has been carried out.");
            send(r, "APPROVAL_DECIDED", title, lines, link, "See the request",
                    List.of(recipients.user(r.getRequestedById())), "APPROVAL:" + r.getId() + ":APPROVED");
        } else if (r.getStatus() == ApprovalStatus.REJECTED && "APPROVAL_REJECTED".equals(event.action())) {
            String title = "Rejected: " + r.getApprovalAction().getLabel().toLowerCase() + " – " + r.getRecordLabel();
            r.getSteps().stream().filter(s -> s.getStatus() == StepStatus.REJECTED).findFirst()
                    .ifPresent(s -> lines.add("Rejected by " + s.getDecidedBy() + ": " + s.getComment()));
            send(r, "APPROVAL_DECIDED", title, lines, link, "See the request",
                    List.of(recipients.user(r.getRequestedById())), "APPROVAL:" + r.getId() + ":REJECTED");
        }
    }

    private void send(ApprovalRequest r, String kind, String title, List<String> lines, String link, String linkText,
                      List<Recipients.Person> people, String dedupKey) {
        NotificationService.Message m = new NotificationService.Message(r.getCompanyId(), r.getBranchId(), kind, title,
                String.join("\n", lines), link, title, renderer.alert(title, lines, link, linkText), dedupKey);
        notificationService.deliver(m, people, null);
    }

    private static Long toLong(Object id) {
        return id instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(id));
    }
}
