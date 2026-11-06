package com.chequetrack.notification.service;

import com.chequetrack.notification.NotificationProperties;
import com.chequetrack.notification.domain.OutboxEmail;
import com.chequetrack.notification.repository.OutboxEmailRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    private static final int BATCH = 50;

    private final OutboxEmailRepository outboxRepository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationProperties properties;
    private final Clock clock;

    public EmailDispatcher(OutboxEmailRepository outboxRepository, ObjectProvider<JavaMailSender> mailSender,
                           NotificationProperties properties, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mail.dispatch-delay-ms:60000}", initialDelayString = "${app.mail.dispatch-delay-ms:60000}")
    @Transactional
    public void dispatch() {
        List<OutboxEmail> pending = outboxRepository.findPending(Limit.of(BATCH));
        if (pending.isEmpty()) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!properties.mail().enabled() || sender == null) {
            String why = !properties.mail().enabled() ? "Mail is switched off (MAIL_ENABLED=false)" : "No mail server configured (spring.mail.host)";
            pending.forEach(e -> e.notSent(why));
            return;
        }
        for (OutboxEmail e : pending) {
            try {
                MimeMessage msg = sender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
                h.setFrom(properties.mail().from());
                h.setTo(e.getToAddress());
                h.setSubject(e.getSubject());
                h.setText(e.getBodyHtml(), true);
                sender.send(msg);
                e.sent(clock.instant());
            } catch (Exception ex) {
                log.warn("Email {} to {} failed: {}", e.getId(), e.getToAddress(), ex.getMessage());
                e.failed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }
}
