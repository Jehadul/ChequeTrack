package com.chequetrack.notification.web;

import com.chequetrack.notification.service.NotificationService;
import com.chequetrack.security.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class NotificationBadgeAdvice {

    private final NotificationService notificationService;

    public NotificationBadgeAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/") || CurrentUser.find().isEmpty()) {
            return 0;
        }
        return notificationService.unreadCount();
    }
}
