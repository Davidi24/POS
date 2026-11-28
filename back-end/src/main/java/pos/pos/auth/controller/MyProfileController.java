package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.auth.dto.ChangeMyEmailRequest;
import pos.pos.auth.dto.MyProfileResponse;
import pos.pos.auth.dto.UpdateMyDetailsRequest;
import pos.pos.auth.service.MyProfileService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.UUID;

/** Profile: the signed-in person's own details, email change and photo (user, 2026-11-18). */
@Tag(name = "Authentication / Profile")
@RestController
@RequestMapping("/auth/me")
@RequiredArgsConstructor
public class MyProfileController {

    private final MyProfileService myProfileService;
    private final pos.pos.auth.service.PinService pinService;

    @GetMapping("/profile")
    @Operation(summary = "My details for Profile")
    public MyProfileResponse profile(Authentication authentication) {
        return myProfileService.profile(me(authentication));
    }

    @PutMapping("/details")
    @Operation(summary = "Change my name and phone")
    public MyProfileResponse updateDetails(@Valid @RequestBody UpdateMyDetailsRequest request, Authentication authentication) {
        pos.pos.security.rbac.PinSignIn.refuse(pos.pos.security.rbac.PinSignIn.YOUR_ACCOUNT);
        return myProfileService.updateDetails(me(authentication), request);
    }

    @PostMapping("/email")
    @Operation(summary = "Ask to change my email (a link goes to the new address)")
    public MyProfileResponse changeEmail(@Valid @RequestBody ChangeMyEmailRequest request, Authentication authentication) {
        pos.pos.security.rbac.PinSignIn.refuse(pos.pos.security.rbac.PinSignIn.YOUR_ACCOUNT);
        return myProfileService.requestEmailChange(me(authentication), request);
    }

    @PutMapping("/username")
    @Operation(summary = "Change my username (3 times in 30 days at most)")
    public MyProfileResponse changeUsername(@Valid @RequestBody pos.pos.auth.dto.ChangeMyUsernameRequest request, Authentication authentication) {
        pos.pos.security.rbac.PinSignIn.refuse(pos.pos.security.rbac.PinSignIn.YOUR_ACCOUNT);
        return myProfileService.changeUsername(me(authentication), request);
    }

    @GetMapping("/pin")
    @Operation(summary = "My PIN: whether there is one and what's left today")
    public pos.pos.auth.service.PinService.PinStatus pinStatus(Authentication authentication) {
        return pinService.status(me(authentication));
    }

    @PostMapping("/pin/suggestion")
    @Operation(summary = "A new random PIN to choose (10 a day)")
    public pos.pos.auth.service.PinService.PinSuggestion suggestPin(Authentication authentication) {
        pos.pos.security.rbac.PinSignIn.refuse(pos.pos.security.rbac.PinSignIn.YOUR_ACCOUNT);
        return pinService.suggest(me(authentication));
    }

    /** Saves a PIN that was suggested to me in the last 30 minutes (3 a day). */
    public record SavePinRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 10) String pin) {
    }

    @PutMapping("/pin")
    @Operation(summary = "Use a suggested PIN")
    public pos.pos.auth.service.PinService.PinStatus savePin(@Valid @RequestBody SavePinRequest request, Authentication authentication) {
        pos.pos.security.rbac.PinSignIn.refuse(pos.pos.security.rbac.PinSignIn.YOUR_ACCOUNT);
        return pinService.save(me(authentication), request.pin());
    }

    /** The app's language (en, it, es, sq). */
    public record LanguageRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 5) String language) {
    }

    @PutMapping("/language")
    @Operation(summary = "My language in the app")
    public org.springframework.http.ResponseEntity<Void> changeLanguage(@Valid @RequestBody LanguageRequest request, Authentication authentication) {
        myProfileService.changeLanguage(me(authentication), request.language());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @DeleteMapping("/email")
    @Operation(summary = "Cancel my waiting email change")
    public MyProfileResponse cancelEmailChange(Authentication authentication) {
        pos.pos.security.rbac.PinSignIn.refuse(pos.pos.security.rbac.PinSignIn.YOUR_ACCOUNT);
        return myProfileService.cancelEmailChange(me(authentication));
    }

    @PutMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my photo")
    public MyProfileResponse uploadPhoto(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return myProfileService.uploadPhoto(me(authentication), file);
    }

    @DeleteMapping("/photo")
    @Operation(summary = "Remove my photo")
    public MyProfileResponse removePhoto(Authentication authentication) {
        return myProfileService.removePhoto(me(authentication));
    }

    private static UUID me(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).getId();
    }
}
