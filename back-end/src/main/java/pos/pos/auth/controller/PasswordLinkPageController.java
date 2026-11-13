package pos.pos.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import pos.pos.auth.dto.PasswordLinkView;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.enums.PasswordLinkPurpose;
import pos.pos.auth.enums.PasswordLinkStatus;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.auth.service.PasswordRules;
import pos.pos.exception.auth.AuthException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * The page the invitation and reset emails link to: the owner (or anyone resetting) chooses a password here, in any
 * browser, without the app. Plain HTML that works on any phone; the script only ticks the rules as you type.
 */
@RestController
@RequestMapping("/public/account/password")
@RequiredArgsConstructor
public class PasswordLinkPageController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetService passwordResetService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@RequestParam(required = false) String token, @RequestParam(required = false) String done) {
        if (done != null) {
            boolean firstPassword = "invite".equals(done);
            return page(HttpStatus.OK, firstPassword ? "Password set" : "Password changed", null,
                    "<div class='badge ok'>&#10003;</div>"
                            + "<h1>" + (firstPassword ? "Your password is set" : "Your password is changed") + "</h1>"
                            + "<p>Open the POS app and sign in with your username or email and your new password.</p>"
                            + (firstPassword ? "" : "<p class='muted'>For your safety, you were signed out on your other devices.</p>")
                            + "<p class='muted'>You can close this page.</p>");
        }
        PasswordLinkView link = passwordResetService.checkLink(token);
        if (link.status() != PasswordLinkStatus.VALID) {
            return problem(link);
        }
        return form(HttpStatus.OK, token, link, null);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> setPassword(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirm
    ) {
        PasswordLinkView link = passwordResetService.checkLink(token);
        if (link.status() != PasswordLinkStatus.VALID) {
            return problem(link);
        }
        String problem = PasswordRules.problem(password);
        if (problem == null && !password.equals(confirm)) {
            problem = "The two passwords aren't the same.";
        }
        if (problem != null) {
            return form(HttpStatus.BAD_REQUEST, token, link, problem);
        }
        try {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken(token);
            request.setNewPassword(password);
            passwordResetService.resetPassword(request);
        } catch (AuthException error) {
            // Used or replaced in the meantime: say what the link says now.
            PasswordLinkView now = passwordResetService.checkLink(token);
            return now.status() == PasswordLinkStatus.VALID ? form(HttpStatus.BAD_REQUEST, token, now, error.getMessage()) : problem(now);
        }
        // Back to a page without the link, so refreshing doesn't send the form again.
        String done = link.purpose() == PasswordLinkPurpose.INVITE ? "invite" : "reset";
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/public/account/password?done=" + done)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private ResponseEntity<String> form(HttpStatus status, String token, PasswordLinkView link, String error) {
        boolean invite = link.purpose() == PasswordLinkPurpose.INVITE;
        String name = link.firstName() == null || link.firstName().isBlank() ? "there" : link.firstName().trim();
        String who = link.username() == null || link.username().isBlank()
                ? "<b>" + esc(link.email()) + "</b>"
                : "<b>" + esc(link.username()) + "</b> or <b>" + esc(link.email()) + "</b>";
        StringBuilder body = new StringBuilder()
                .append("<h1>").append(invite ? "Set your password" : "Choose a new password").append("</h1>")
                .append("<p>Hi ").append(esc(name)).append(", ")
                .append(invite
                        ? (link.restaurantName() == null ? "your POS account is ready." : "you're the owner of <b>" + esc(link.restaurantName()) + "</b> on POS.")
                                + " Choose the password you'll use to sign in."
                        : "choose a new password for your POS account.")
                .append("</p>")
                .append("<p class='muted'>You'll sign in as ").append(who).append(".</p>");
        if (error != null) {
            body.append("<p class='error' role='alert'>").append(esc(error)).append("</p>");
        }
        body.append("<form method='post' action='/public/account/password' autocomplete='on'>")
                .append("<input type='hidden' name='token' value='").append(esc(token)).append("'>")
                // Lets password managers save the new password under the right name.
                .append("<input type='text' name='username' autocomplete='username' value='")
                .append(esc(link.username() == null ? link.email() : link.username())).append("' hidden readonly>")
                .append("<label for='password'>New password</label>")
                .append("<input id='password' name='password' type='password' autocomplete='new-password' required minlength='")
                .append(PasswordRules.MIN_LENGTH).append("' maxlength='").append(PasswordRules.MAX_LENGTH).append("' autofocus>")
                .append("<label for='confirm'>Type it again</label>")
                .append("<input id='confirm' name='confirm' type='password' autocomplete='new-password' required maxlength='")
                .append(PasswordRules.MAX_LENGTH).append("'>")
                .append("<label class='show'><input id='show' type='checkbox'> Show the password</label>")
                .append("<ul class='rules'>")
                .append("<li data-rule='length'>At least 8 characters</li>")
                .append("<li data-rule='upper'>An uppercase letter</li>")
                .append("<li data-rule='digit'>A number</li>")
                .append("<li data-rule='special'>A special character, like ! or #</li>")
                .append("<li data-rule='same'>Both passwords are the same</li>")
                .append("</ul>")
                .append("<button type='submit'>").append(invite ? "Set my password" : "Change my password").append("</button>")
                .append("</form>");
        if (link.expiresAt() != null) {
            body.append("<p class='muted'>This link works once and expires in ")
                    .append(inWords(Duration.between(OffsetDateTime.now(ZoneOffset.UTC), link.expiresAt()))).append(".</p>");
        }
        String nonce = nonce();
        body.append("<script nonce='").append(nonce).append("'>")
                .append("(function(){var p=document.getElementById('password'),c=document.getElementById('confirm'),s=document.getElementById('show');")
                .append("var t={length:function(v){return v.length>=8},upper:function(v){return /\\p{Lu}/u.test(v)},")
                .append("digit:function(v){return /\\p{Nd}/u.test(v)},special:function(v){return /[^\\p{L}\\p{Nd}]/u.test(v)},")
                .append("same:function(v){return v.length>0&&v===c.value}};")
                .append("function u(){document.querySelectorAll('.rules li').forEach(function(li){li.classList.toggle('met',t[li.dataset.rule](p.value))})}")
                .append("p.addEventListener('input',u);c.addEventListener('input',u);")
                .append("s.addEventListener('change',function(){var k=s.checked?'text':'password';p.type=k;c.type=k});u();})();")
                .append("</script>");
        return page(status, invite ? "Set your password" : "Choose a new password", nonce, body.toString());
    }

    private ResponseEntity<String> problem(PasswordLinkView link) {
        String title = switch (link.status()) {
            case EXPIRED -> "This link has expired";
            case USED -> "This link was already used";
            case REPLACED -> "There's a newer email";
            case UNAVAILABLE -> "This account is switched off";
            default -> "This link doesn't work";
        };
        HttpStatus status = link.status() == PasswordLinkStatus.INVALID ? HttpStatus.NOT_FOUND : HttpStatus.GONE;
        return page(status, title, null, "<div class='badge warn'>!</div><h1>" + esc(title) + "</h1><p>" + esc(link.message()) + "</p>");
    }

    private static ResponseEntity<String> page(HttpStatus status, String title, String nonce, String body) {
        String html = "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<meta name='robots' content='noindex'><title>" + esc(title) + " · POS</title><style>"
                + "body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#f7f8f6;color:#222426;margin:0;padding:16px}"
                + "main{max-width:440px;margin:6vh auto 0;background:#fff;border:1px solid #e3e6e1;border-radius:16px;padding:24px 24px 14px}"
                + "h1{font-size:22px;margin:0 0 8px}p{line-height:1.45;margin:8px 0}"
                + "label{display:block;font-size:13px;font-weight:600;margin:14px 0 0}"
                + "input[type=password],input[type=text]{display:block;width:100%;box-sizing:border-box;margin-top:5px;padding:11px;border:1px solid #d5d8d2;border-radius:9px;font-size:16px}"
                + "input:focus{outline:2px solid #4f7942;outline-offset:1px}"
                + "label.show{font-weight:500;color:#5d605b;display:flex;align-items:center;gap:6px}"
                + "button{width:100%;background:#4f7942;color:#fff;border:0;border-radius:10px;padding:13px 18px;font-size:16px;font-weight:600;margin:14px 0 6px;cursor:pointer}"
                + ".muted{color:#747572;font-size:13px}.error{color:#b13a2f;font-weight:600}"
                + ".rules{list-style:none;padding:0;margin:12px 0 0;font-size:13px;color:#747572}"
                + ".rules li{margin:5px 0;padding-left:22px;position:relative}"
                + ".rules li:before{content:'';position:absolute;left:0;top:2px;width:12px;height:12px;border:1.5px solid #c4c7c1;border-radius:50%}"
                + ".rules li.met{color:#2f6b2a}.rules li.met:before{background:#4f7942;border-color:#4f7942}"
                + ".badge{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;margin-bottom:10px}"
                + ".badge.ok{background:#e6efe1;color:#4f7942}.badge.warn{background:#fbefe0;color:#b46a0b}"
                + "</style></head><body><main>" + body + "</main></body></html>";
        String scripts = nonce == null ? "'none'" : "'nonce-" + nonce + "'";
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                // The link carries a one-time key: never pass it on to other sites.
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src " + scripts
                        + "; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
                .body(html);
    }

    // "30 minutes", "5 hours", "3 days".
    private static String inWords(Duration left) {
        long minutes = Math.max(1, left.toMinutes());
        if (minutes < 120) return minutes + (minutes == 1 ? " minute" : " minutes");
        long hours = left.toHours();
        if (hours < 48) return hours + " hours";
        return left.toDays() + " days";
    }

    private static String nonce() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
