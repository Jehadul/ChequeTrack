package com.chequetrack.notification.web;

import com.chequetrack.notification.NotificationPermissions;
import com.chequetrack.notification.NotificationProperties;
import com.chequetrack.notification.domain.ReminderRun;
import com.chequetrack.notification.service.NotificationService;
import com.chequetrack.notification.service.ReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private static final String MANAGE = "hasAuthority('" + NotificationPermissions.NOTIFY_MANAGE + "')";

    private final NotificationService notificationService;
    private final ReminderService reminderService;
    private final NotificationProperties properties;

    public NotificationController(NotificationService notificationService, ReminderService reminderService,
                                  NotificationProperties properties) {
        this.notificationService = notificationService;
        this.reminderService = reminderService;
        this.properties = properties;
    }

    @GetMapping
    public String mine(Model model) {
        model.addAttribute("notifications", notificationService.mine());
        return "notification/list";
    }

    @GetMapping("/{id}/open")
    public String open(@PathVariable Long id) {
        String link = notificationService.open(id);
        boolean internal = link != null && link.startsWith("/") && !link.startsWith("//");
        return "redirect:" + (internal ? link : "/notifications");
    }

    @PostMapping("/read-all")
    public String readAll(RedirectAttributes redirect) {
        int n = notificationService.markAllRead();
        redirect.addFlashAttribute("successMessage", n + " notification(s) marked read.");
        return "redirect:/notifications";
    }

    // ---- administrator -------------------------------------------------------------------------

    @GetMapping("/admin")
    @PreAuthorize(MANAGE)
    public String admin(Model model) {
        model.addAttribute("runs", notificationService.runs());
        model.addAttribute("emails", notificationService.outbox());
        model.addAttribute("mailEnabled", properties.mail().enabled());
        model.addAttribute("cron", properties.reminders().cron());
        return "notification/admin";
    }

    @GetMapping("/admin/emails/{id}")
    @PreAuthorize(MANAGE)
    public String email(@PathVariable Long id, Model model) {
        model.addAttribute("email", notificationService.email(id));
        return "notification/email";
    }

    @PostMapping("/admin/run")
    @PreAuthorize(MANAGE)
    public String runNow(RedirectAttributes redirect) {
        ReminderRun run = reminderService.runNow();
        redirect.addFlashAttribute("successMessage", run.getBranches() == 0
                ? "Reminders run: no branch has anything to report today."
                : "Reminders run: " + run.getItems() + " item(s) in " + run.getBranches() + " branch(es); "
                + run.getEmails() + " email(s) queued, " + run.getNotifications() + " notification(s).");
        return "redirect:/notifications/admin";
    }
}
