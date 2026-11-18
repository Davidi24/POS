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
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.exception.auth.AuthException;

import static pos.pos.auth.controller.PasswordLinkPageController.esc;
import static pos.pos.auth.controller.PasswordLinkPageController.page;

/**
 * The page confirmation emails link to (user, 2026-11-18): new staff confirm their email, and someone changing their email
 * confirms the new one, in any browser. Opening the link changes nothing (mail scanners open links); the button does.
 */
@RestController
@RequestMapping("/public/account/email")
@RequiredArgsConstructor
public class EmailLinkPageController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@RequestParam(required = false) String token, @RequestParam(required = false) String done) {
        if (done != null) {
            boolean changed = "changed".equals(done);
            return PasswordLinkPageController.page(HttpStatus.OK, "Email confirmed", null,
                    "<div class='badge ok'>&#10003;</div><h1>" + (changed ? "Your new email is set" : "Your email is confirmed") + "</h1>"
                            + "<p>" + (changed ? "Use it from now on to sign in and to get your emails." : "You can sign in to the POS app now.") + "</p>"
                            + "<p class='muted'>You can close this page.</p>");
        }
        EmailVerificationService.EmailLinkView link = emailVerificationService.checkLink(token);
        if (!"VALID".equals(link.status())) {
            return problem(link.status());
        }
        boolean change = link.newEmail() != null;
        String name = link.firstName() == null || link.firstName().isBlank() ? "there" : link.firstName().trim();
        String body = "<h1>" + (change ? "Confirm your new email" : "Confirm your email") + "</h1>"
                + "<p>Hi " + esc(name) + ", " + (change
                    ? "your POS account will use <b>" + esc(link.newEmail()) + "</b> instead of " + esc(link.email()) + "."
                    : "confirm <b>" + esc(link.email()) + "</b> for your POS account.") + "</p>"
                + "<form method='post' action='/public/account/email'>"
                + "<input type='hidden' name='token' value='" + esc(token) + "'>"
                + "<button type='submit'>" + (change ? "Use my new email" : "Confirm my email") + "</button>"
                + "</form>";
        return PasswordLinkPageController.page(HttpStatus.OK, change ? "Confirm your new email" : "Confirm your email", null, body);
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirm(@RequestParam(required = false) String token) {
        EmailVerificationService.EmailLinkView link = emailVerificationService.checkLink(token);
        if (!"VALID".equals(link.status())) {
            return problem(link.status());
        }
        try {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken(token.trim());
            emailVerificationService.verifyEmail(request);
        } catch (AuthException error) {
            return PasswordLinkPageController.page(HttpStatus.CONFLICT, "Not changed", null,
                    "<div class='badge warn'>!</div><h1>Not changed</h1><p>" + esc(error.getMessage()) + "</p>");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/public/account/email?done=" + (link.newEmail() != null ? "changed" : "confirmed"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private static ResponseEntity<String> problem(String status) {
        String title = switch (status) {
            case "EXPIRED" -> "This link has expired";
            case "USED" -> "This link was already used";
            default -> "This link doesn't work";
        };
        String line = switch (status) {
            case "EXPIRED" -> "Ask for a new one: in the POS app, Profile, or ask your manager.";
            case "USED" -> "Your email is already confirmed.";
            default -> "Check that you copied the whole link from the email.";
        };
        return PasswordLinkPageController.page("INVALID".equals(status) ? HttpStatus.NOT_FOUND : HttpStatus.GONE, title, null,
                "<div class='badge warn'>!</div><h1>" + esc(title) + "</h1><p>" + esc(line) + "</p>");
    }
}
