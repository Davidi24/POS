package pos.pos.mail;

import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import pos.pos.config.properties.AppMailProperties;

import java.util.Properties;

/**
 * The sender for account emails (user, 2026-11-18: "only the emails built till now use Gmail; booking emails we discuss
 * later"): invitations, password links, email confirmation and changes, username changes, limits, applications. Set by
 * `app.mail.account.*` (ACCOUNT_MAIL_*); without them it's the usual mail server (spring.mail). Booking and guest emails
 * keep using the usual one.
 */
@Component
public class AccountMailSender {

    private final JavaMailSender sender;
    private final String from;
    private final boolean own;

    public AccountMailSender(JavaMailSender defaultSender, AppMailProperties appMail, Environment env) {
        String host = env.getProperty("app.mail.account.host", "");
        if (host.isBlank()) {
            this.sender = defaultSender;
            this.from = appMail.getFrom();
            this.own = false;
            return;
        }
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host.trim());
        impl.setPort(Integer.parseInt(env.getProperty("app.mail.account.port", "587").trim()));
        impl.setUsername(env.getProperty("app.mail.account.username"));
        impl.setPassword(env.getProperty("app.mail.account.password"));
        impl.setDefaultEncoding("UTF-8");
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        this.sender = impl;
        String accountFrom = env.getProperty("app.mail.account.from", "");
        this.from = accountFrom.isBlank() ? appMail.getFrom() : accountFrom.trim();
        this.own = true;
    }

    /** Sends [message] from the account sender's address (unless it names one). */
    public void send(SimpleMailMessage message) {
        if (message.getFrom() == null || !own) message.setFrom(from);
        sender.send(message);
    }

    public String from() {
        return from;
    }
}
