package com.chequetrack.notification.service;

import com.chequetrack.chequebook.ChequeBookPermissions;
import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.notification.NotificationPermissions;
import com.chequetrack.notification.domain.ReminderRun;
import com.chequetrack.notification.repository.ReminderRunRepository;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final String[] BRANCH_STAFF = {ReceivedPermissions.RECEIVED_MANAGE, IssuedPermissions.ISSUED_MANAGE,
            ChequeBookPermissions.CHEQUEBOOK_MANAGE};

    private final Recipients recipients;
    private final ReminderQueries queries;
    private final MailRenderer renderer;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final ReminderRunRepository runRepository;
    private final TransactionTemplate tx;
    private final DisplayFormat format;
    private final Clock clock;

    public ReminderService(Recipients recipients, ReminderQueries queries, MailRenderer renderer,
                           NotificationService notificationService, SettingsService settingsService,
                           ReminderRunRepository runRepository, PlatformTransactionManager transactionManager,
                           DisplayFormat format, Clock clock) {
        this.recipients = recipients;
        this.queries = queries;
        this.renderer = renderer;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.runRepository = runRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.format = format;
        this.clock = clock;
    }

    /** The scheduled run: every company, at most once per branch and day. */
    public void runScheduled() {
        LocalDate today = LocalDate.now(clock);
        for (Long companyId : recipients.companies()) {
            try {
                tx.executeWithoutResult(s -> runCompany(companyId, today, "scheduler", false));
            } catch (RuntimeException ex) {
                log.error("Daily reminders failed for company {}", companyId, ex);
            }
        }
    }

    /** Run by hand from the notifications page, e.g. to show the emails: sent again even if sent today. */
    @PreAuthorize("hasAuthority('" + NotificationPermissions.NOTIFY_MANAGE + "')")
    public ReminderRun runNow() {
        Long companyId = CurrentUser.get().getCompanyId();
        String user = CurrentUser.get().getUsername();
        return tx.execute(s -> runCompany(companyId, LocalDate.now(clock), user, true));
    }

    private ReminderRun runCompany(Long companyId, LocalDate today, String runBy, boolean manual) {
        Instant now = clock.instant();
        CompanySettings settings = settingsService.forCompany(companyId);
        ReminderRun run = runRepository.save(ReminderRun.start(companyId, today, runBy, now));
        for (Recipients.BranchInfo branch : recipients.activeBranches(companyId)) {
            Digest digest = queries.digest(branch, settings, today, now);
            if (digest.isEmpty()) {
                continue;
            }
            List<Recipients.Person> staff = recipients.branchStaff(companyId, branch.id(), BRANCH_STAFF);
            String key = "DIGEST:" + branch.id() + ":" + today + (manual ? ":" + now.getEpochSecond() : "");
            String title = "Daily reminder " + branch.code() + ": " + digest.summary();
            NotificationService.Message m = new NotificationService.Message(companyId, branch.id(), "DAILY_REMINDER",
                    title, plainText(digest), "/notifications", "ChequeTrack " + branch.code() + " – " + today + ": "
                    + digest.summary(), renderer.digest(digest), key);
            int[] sent = notificationService.deliver(m, staff, branch.email());
            run.add(digest.itemCount(), sent[1], sent[0]);
        }
        return run;
    }

    private String plainText(Digest d) {
        StringBuilder sb = new StringBuilder();
        for (Digest.Section s : d.sections()) {
            sb.append(s.title()).append(":\n");
            for (Digest.Item i : s.items()) {
                sb.append("• ").append(i.number());
                if (i.party() != null) {
                    sb.append(" · ").append(i.party());
                }
                if (i.amount() != null) {
                    sb.append(" · Tk ").append(format.amount(i.amount()));
                }
                sb.append(" – ").append(i.note()).append('\n');
            }
        }
        return sb.toString().strip();
    }
}
