package com.chequetrack.notification.service;

import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.notification.NotificationProperties;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

@Component
public class MailRenderer {

    private final ITemplateEngine templateEngine;
    private final DisplayFormat format;
    private final NotificationProperties properties;

    public MailRenderer(ITemplateEngine templateEngine, DisplayFormat format, NotificationProperties properties) {
        this.templateEngine = templateEngine;
        this.format = format;
        this.properties = properties;
    }

    public String digest(Digest digest) {
        Context ctx = context();
        ctx.setVariable("d", digest);
        return templateEngine.process("mail/digest", ctx);
    }

    /** A short alert: a heading, a few lines and a button to the record. */
    public String alert(String heading, List<String> lines, String linkPath, String linkText) {
        Context ctx = context();
        ctx.setVariable("heading", heading);
        ctx.setVariable("lines", lines);
        ctx.setVariable("link", linkPath == null ? null : properties.link(linkPath));
        ctx.setVariable("linkText", linkText);
        return templateEngine.process("mail/alert", ctx);
    }

    private Context context() {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("fmt", format);
        ctx.setVariable("home", properties.link("/"));
        ctx.setVariable("settingsLink", properties.link("/notifications"));
        return ctx;
    }
}
