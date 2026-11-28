package pos.pos.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
import pos.pos.i18n.PageLanguage;
import pos.pos.i18n.Words;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * The page the invitation and reset emails link to: the owner (or anyone resetting) chooses a password here, in any
 * browser, without the app. Plain HTML that works on any phone; the script only ticks the rules as you type. In the
 * person's own language, or the one picked at the top (user, 2026-11-18; {@link PageLanguage}).
 */
@RestController
@RequestMapping("/public/account/password")
@RequiredArgsConstructor
public class PasswordLinkPageController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetService passwordResetService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@RequestParam(required = false) String token, @RequestParam(required = false) String done,
                                       @RequestParam(required = false) String lang,
                                       @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String browser) {
        if (done != null) {
            String l = PageLanguage.pick(lang, null, browser);
            boolean firstPassword = "invite".equals(done);
            return page(HttpStatus.OK, l, code -> "/public/account/password?done=" + (firstPassword ? "invite" : "reset") + "&lang=" + code,
                    Words.t(l, firstPassword ? "Password set" : "Password changed"), null,
                    "<div class='badge ok'>&#10003;</div>"
                            + "<h1>" + Words.t(l, firstPassword ? "Your password is set" : "Your password is changed") + "</h1>"
                            + "<p>" + Words.t(l, "Open the POS app and sign in with your username or email and your new password.") + "</p>"
                            + (firstPassword ? "" : "<p class='muted'>" + Words.t(l, "For your safety, you were signed out on your other devices.") + "</p>")
                            + "<p class='muted'>" + Words.t(l, "You can close this page.") + "</p>");
        }
        PasswordLinkView link = passwordResetService.checkLink(token);
        String l = PageLanguage.pick(lang, link.language(), browser);
        if (link.status() != PasswordLinkStatus.VALID) {
            return problem(link, l, token);
        }
        return form(HttpStatus.OK, token, link, null, l);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> setPassword(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirm,
            @RequestParam(required = false) String lang
    ) {
        PasswordLinkView link = passwordResetService.checkLink(token);
        String l = PageLanguage.pick(lang, link.language(), null);
        if (link.status() != PasswordLinkStatus.VALID) {
            return problem(link, l, token);
        }
        String problem = PasswordRules.problem(password);
        if (problem == null && !password.equals(confirm)) {
            problem = Words.en("The two passwords aren't the same.");
        }
        if (problem != null) {
            return form(HttpStatus.BAD_REQUEST, token, link, Words.tr(l, problem), l);
        }
        try {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken(token);
            request.setNewPassword(password);
            passwordResetService.resetPassword(request);
        } catch (AuthException error) {
            // Used or replaced in the meantime: say what the link says now.
            PasswordLinkView now = passwordResetService.checkLink(token);
            return now.status() == PasswordLinkStatus.VALID ? form(HttpStatus.BAD_REQUEST, token, now, Words.tr(l, error.getMessage()), l) : problem(now, l, token);
        }
        // Back to a page without the link, so refreshing doesn't send the form again.
        String done = link.purpose() == PasswordLinkPurpose.INVITE ? "invite" : "reset";
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/public/account/password?done=" + done + "&lang=" + l)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private ResponseEntity<String> form(HttpStatus status, String token, PasswordLinkView link, String error, String l) {
        boolean invite = link.purpose() == PasswordLinkPurpose.INVITE;
        String name = esc(link.firstName() == null || link.firstName().isBlank() ? Words.t(l, "there") : link.firstName().trim());
        String who = link.username() == null || link.username().isBlank()
                ? "<b>" + esc(link.email()) + "</b>"
                : Words.t(l, "{} or {}", "<b>" + esc(link.username()) + "</b>", "<b>" + esc(link.email()) + "</b>");
        String greeting = !invite ? Words.t(l, "Hi {}, choose a new password for your POS account.", name)
                : link.restaurantName() == null ? Words.t(l, "Hi {}, your POS account is ready. Choose the password you'll use to sign in.", name)
                : Words.t(l, "Hi {}, you're the owner of {} on POS. Choose the password you'll use to sign in.", name,
                        "<b>" + esc(link.restaurantName()) + "</b>");
        StringBuilder body = new StringBuilder()
                .append("<h1>").append(Words.t(l, invite ? "Set your password" : "Choose a new password")).append("</h1>")
                .append("<p>").append(greeting).append("</p>")
                .append("<p class='muted'>").append(Words.t(l, "You'll sign in as {}.", who)).append("</p>");
        if (error != null) {
            body.append("<p class='error' role='alert'>").append(esc(error)).append("</p>");
        }
        body.append("<form method='post' action='/public/account/password' autocomplete='on'>")
                .append("<input type='hidden' name='token' value='").append(esc(token)).append("'>")
                .append("<input type='hidden' name='lang' value='").append(l).append("'>")
                // Lets password managers save the new password under the right name.
                .append("<input type='text' name='username' autocomplete='username' value='")
                .append(esc(link.username() == null ? link.email() : link.username())).append("' hidden readonly>")
                .append("<label for='password'>").append(Words.t(l, "New password")).append("</label>")
                .append("<input id='password' name='password' type='password' autocomplete='new-password' required minlength='")
                .append(PasswordRules.MIN_LENGTH).append("' maxlength='").append(PasswordRules.MAX_LENGTH).append("' autofocus>")
                .append("<label for='confirm'>").append(Words.t(l, "Type it again")).append("</label>")
                .append("<input id='confirm' name='confirm' type='password' autocomplete='new-password' required maxlength='")
                .append(PasswordRules.MAX_LENGTH).append("'>")
                .append("<label class='show'><input id='show' type='checkbox'> ").append(Words.t(l, "Show the password")).append("</label>")
                .append("<ul class='rules'>")
                .append("<li data-rule='length'>").append(Words.t(l, "At least 8 characters")).append("</li>")
                .append("<li data-rule='upper'>").append(Words.t(l, "An uppercase letter")).append("</li>")
                .append("<li data-rule='digit'>").append(Words.t(l, "A number")).append("</li>")
                .append("<li data-rule='special'>").append(Words.t(l, "A special character, like ! or #")).append("</li>")
                .append("<li data-rule='same'>").append(Words.t(l, "Both passwords are the same")).append("</li>")
                .append("</ul>")
                // A reset signs out every device; say so before (user, 2026-11-18).
                .append(invite ? "" : "<p class='muted'>" + Words.t(l, "For your safety, you'll be signed out on all your devices.") + "</p>")
                .append("<button type='submit'>").append(Words.t(l, invite ? "Set my password" : "Change my password")).append("</button>")
                .append("</form>");
        if (link.expiresAt() != null) {
            body.append("<p class='muted'>").append(Words.t(l, "This link works once and expires in {}.",
                    pos.pos.auth.service.AuthMailService.inWords(l, Duration.between(OffsetDateTime.now(ZoneOffset.UTC), link.expiresAt())))).append("</p>");
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
        return page(status, l, code -> linkTo(token, code), Words.t(l, invite ? "Set your password" : "Choose a new password"), nonce, body.toString());
    }

    private ResponseEntity<String> problem(PasswordLinkView link, String l, String token) {
        String title = switch (link.status()) {
            case EXPIRED -> Words.t(l, "This link has expired");
            case USED -> Words.t(l, "This link was already used");
            case REPLACED -> Words.t(l, "There's a newer email");
            case UNAVAILABLE -> Words.t(l, "This account is switched off");
            default -> Words.t(l, "This link doesn't work");
        };
        HttpStatus status = link.status() == PasswordLinkStatus.INVALID ? HttpStatus.NOT_FOUND : HttpStatus.GONE;
        return page(status, l, code -> linkTo(token, code), title, null,
                "<div class='badge warn'>!</div><h1>" + esc(title) + "</h1><p>" + esc(Words.tr(l, link.message())) + "</p>");
    }

    private static String linkTo(String token, String lang) {
        return "/public/account/password?" + (token == null ? "" : "token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8) + "&") + "lang=" + lang;
    }

    /** A page in [lang] with the four languages at its top, each linking to [hrefFor] its code. */
    static ResponseEntity<String> page(HttpStatus status, String lang, java.util.function.Function<String, String> hrefFor, String title,
                                       String nonce, String body) {
        String html = "<!doctype html><html lang='" + lang + "'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
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
                + PageLanguage.STYLE
                + "</style></head><body><main>" + PageLanguage.switcher(lang, hrefFor) + body + "</main></body></html>";
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

    private static String nonce() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
