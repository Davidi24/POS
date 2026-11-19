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
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.exception.auth.AuthException;
import pos.pos.i18n.PageLanguage;
import pos.pos.i18n.Words;

import static pos.pos.auth.controller.PasswordLinkPageController.esc;

/**
 * The page confirmation emails link to (user, 2026-11-18): new staff confirm their email, and someone changing their email
 * confirms the new one, in any browser. Opening the link changes nothing (mail scanners open links); the button does. In
 * the person's own language, or the one picked at the top (user, 2026-11-18).
 */
@RestController
@RequestMapping("/public/account/email")
@RequiredArgsConstructor
public class EmailLinkPageController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@RequestParam(required = false) String token, @RequestParam(required = false) String done,
                                       @RequestParam(required = false) String lang,
                                       @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String browser) {
        if (done != null) {
            String l = PageLanguage.pick(lang, null, browser);
            boolean changed = "changed".equals(done);
            return PasswordLinkPageController.page(HttpStatus.OK, l, code -> "/public/account/email?done=" + (changed ? "changed" : "confirmed") + "&lang=" + code,
                    Words.t(l, "Email confirmed"), null,
                    "<div class='badge ok'>&#10003;</div><h1>" + Words.t(l, changed ? "Your new email is set" : "Your email is confirmed") + "</h1>"
                            + "<p>" + Words.t(l, changed ? "Use it from now on to sign in and to get your emails." : "You can sign in to the POS app now.") + "</p>"
                            + "<p class='muted'>" + Words.t(l, "You can close this page.") + "</p>");
        }
        EmailVerificationService.EmailLinkView link = emailVerificationService.checkLink(token);
        String l = PageLanguage.pick(lang, link.language(), browser);
        if (!"VALID".equals(link.status())) {
            return problem(link.status(), l, token);
        }
        boolean change = link.newEmail() != null;
        String name = esc(link.firstName() == null || link.firstName().isBlank() ? Words.t(l, "there") : link.firstName().trim());
        String body = "<h1>" + Words.t(l, change ? "Confirm your new email" : "Confirm your email") + "</h1>"
                + "<p>" + (change
                    ? Words.t(l, "Hi {}, your POS account will use {} instead of {}.", name, "<b>" + esc(link.newEmail()) + "</b>", esc(link.email()))
                    : Words.t(l, "Hi {}, confirm {} for your POS account.", name, "<b>" + esc(link.email()) + "</b>")) + "</p>"
                + "<form method='post' action='/public/account/email'>"
                + "<input type='hidden' name='token' value='" + esc(token) + "'>"
                + "<input type='hidden' name='lang' value='" + l + "'>"
                + "<button type='submit'>" + Words.t(l, change ? "Use my new email" : "Confirm my email") + "</button>"
                + "</form>";
        return PasswordLinkPageController.page(HttpStatus.OK, l, code -> linkTo(token, code),
                Words.t(l, change ? "Confirm your new email" : "Confirm your email"), null, body);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirm(@RequestParam(required = false) String token, @RequestParam(required = false) String lang) {
        EmailVerificationService.EmailLinkView link = emailVerificationService.checkLink(token);
        String l = PageLanguage.pick(lang, link.language(), null);
        if (!"VALID".equals(link.status())) {
            return problem(link.status(), l, token);
        }
        try {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken(token.trim());
            emailVerificationService.verifyEmail(request);
        } catch (AuthException error) {
            return PasswordLinkPageController.page(HttpStatus.CONFLICT, l, code -> linkTo(token, code), Words.t(l, "Not changed"), null,
                    "<div class='badge warn'>!</div><h1>" + Words.t(l, "Not changed") + "</h1><p>" + esc(Words.tr(l, error.getMessage())) + "</p>");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/public/account/email?done=" + (link.newEmail() != null ? "changed" : "confirmed") + "&lang=" + l)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private static ResponseEntity<String> problem(String status, String l, String token) {
        String title = switch (status) {
            case "EXPIRED" -> Words.t(l, "This link has expired");
            case "USED" -> Words.t(l, "This link was already used");
            default -> Words.t(l, "This link doesn't work");
        };
        String line = switch (status) {
            case "EXPIRED" -> Words.t(l, "Ask for a new one: in the POS app, Profile, or ask your manager.");
            case "USED" -> Words.t(l, "Your email is already confirmed.");
            default -> Words.t(l, "Check that you copied the whole link from the email.");
        };
        return PasswordLinkPageController.page("INVALID".equals(status) ? HttpStatus.NOT_FOUND : HttpStatus.GONE, l, code -> linkTo(token, code),
                title, null, "<div class='badge warn'>!</div><h1>" + esc(title) + "</h1><p>" + esc(line) + "</p>");
    }

    private static String linkTo(String token, String lang) {
        return "/public/account/email?" + (token == null ? "" : "token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8) + "&") + "lang=" + lang;
    }
}
