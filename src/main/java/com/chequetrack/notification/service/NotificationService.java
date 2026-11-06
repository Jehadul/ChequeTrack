package com.chequetrack.notification.service;

import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.notification.NotificationPermissions;
import com.chequetrack.notification.domain.Notification;
import com.chequetrack.notification.domain.OutboxEmail;
import com.chequetrack.notification.domain.ReminderRun;
import com.chequetrack.notification.repository.NotificationRepository;
import com.chequetrack.notification.repository.OutboxEmailRepository;
import com.chequetrack.notification.repository.ReminderRunRepository;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class NotificationService {

    private static final String MANAGE = "hasAuthority('" + NotificationPermissions.NOTIFY_MANAGE + "')";

    private final NotificationRepository notificationRepository;
    private final OutboxEmailRepository outboxRepository;
    private final ReminderRunRepository runRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               OutboxEmailRepository outboxRepository,
                               ReminderRunRepository runRepository,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    /** What to tell people. {@code body} is plain text for the in-app list; {@code html} is the email. */
    public record Message(Long companyId, Long branchId, String kind, String title, String body, String link,
                          String subject, String html, String dedupKey) {
    }

    /** @return [in-app notifications created, emails queued] */
    @Transactional(propagation = Propagation.MANDATORY)
    public int[] deliver(Message m, List<Recipients.Person> people, String extraAddress) {
        int notes = 0;
        int mails = 0;
        for (Recipients.Person p : people) {
            if (m.dedupKey() == null || !notificationRepository.existsByUserIdAndDedupKey(p.id(), m.dedupKey())) {
                notificationRepository.save(Notification.create(m.companyId(), p.id(), m.kind(), m.title(), m.body(),
                        m.link(), m.dedupKey(), clock.instant()));
                notes++;
            }
            if (p.hasEmail() && queue(m, p.email(), p.name())) {
                mails++;
            }
        }
        if (extraAddress != null && !extraAddress.isBlank() && queue(m, extraAddress, null)) {
            mails++;
        }
        return new int[] {notes, mails};
    }

    private boolean queue(Message m, String address, String name) {
        if (m.dedupKey() != null && outboxRepository.alreadyQueued(address.trim(), m.dedupKey())) {
            return false;
        }
        outboxRepository.save(OutboxEmail.create(m.companyId(), m.branchId(), m.kind(), address, name, m.subject(),
                m.html(), m.dedupKey(), clock.instant()));
        return true;
    }

    // ---- the user's own notifications -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Notification> mine() {
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(CurrentUser.get().getId(), Limit.of(100));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadAtIsNull(CurrentUser.get().getId());
    }

    /** Marks it read and returns where it points. */
    @Transactional
    public String open(Long id) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getUserId().equals(CurrentUser.get().getId()))
                .orElseThrow(() -> new NotFoundException("Notification", id));
        n.markRead(clock.instant());
        return n.getLink();
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(CurrentUser.get().getId(), clock.instant());
    }

    // ---- administrator: outbox and runs ---------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<OutboxEmail> outbox() {
        return outboxRepository.findByCompanyIdOrderByIdDesc(CurrentUser.get().getCompanyId(), Limit.of(200));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public OutboxEmail email(Long id) {
        return outboxRepository.findById(id)
                .filter(e -> e.getCompanyId().equals(CurrentUser.get().getCompanyId()))
                .orElseThrow(() -> new NotFoundException("Email", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<ReminderRun> runs() {
        return runRepository.findByCompanyIdOrderByStartedAtDesc(CurrentUser.get().getCompanyId(), Limit.of(20));
    }
}
