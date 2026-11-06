package com.chequetrack.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 30, updatable = false)
    private String kind;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(columnDefinition = "text", updatable = false)
    private String body;

    @Column(length = 300, updatable = false)
    private String link;

    @Column(name = "dedup_key", length = 150, updatable = false)
    private String dedupKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public static Notification create(Long companyId, Long userId, String kind, String title, String body, String link,
                                      String dedupKey, Instant now) {
        Notification n = new Notification();
        n.companyId = companyId;
        n.userId = userId;
        n.kind = kind;
        n.title = cut(title, 200);
        n.body = body;
        n.link = link;
        n.dedupKey = dedupKey;
        n.createdAt = now;
        return n;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isUnread() {
        return readAt == null;
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
