package cloud.haovo.filemanager.api;

import cloud.haovo.filemanager.service.AuthService;
import cloud.haovo.filemanager.service.UserQuotaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final UserQuotaService userQuotaService;

    public AuthController(AuthService service, UserQuotaService userQuotaService) {
        this.service = service;
        this.userQuotaService = userQuotaService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequests.Register request) {
        return service.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequests.Login request) {
        return service.login(request);
    }

    @PostMapping("/2fa/verify")
    public AuthResponse verifyTwoFactor(@Valid @RequestBody AuthRequests.VerifyTwoFactor request) {
        return service.verifyTwoFactor(request);
    }

    @PostMapping("/email/verify")
    public void verifyEmail(@Valid @RequestBody AuthRequests.VerifyEmail request) {
        service.verifyEmail(request);
    }

    @PostMapping("/email/resend-verification")
    public void resendVerification(@Valid @RequestBody AuthRequests.ResendVerification request) {
        service.resendVerification(request);
    }

    @PostMapping("/password/forgot")
    public void forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword request) {
        service.forgotPassword(request);
    }

    @PostMapping("/password/reset")
    public void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request) {
        service.resetPassword(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody AuthRequests.Refresh request) {
        return service.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody AuthRequests.Refresh request) {
        service.logout(request);
    }

    @GetMapping("/me")
    public AuthResponse.UserResponse me(Authentication authentication) {
        cloud.haovo.filemanager.domain.User user = service.findByEmail(authentication.getName());
        return AuthResponse.UserResponse.from(user, userQuotaService.effectiveQuotaBytes(user),
                userQuotaService.usedBytes(user));
    }

    @PatchMapping("/profile")
    public AuthResponse.UserResponse updateProfile(Authentication authentication,
            @Valid @RequestBody AuthRequests.UpdateProfile request) {
        return service.updateProfile(authentication.getName(), request);
    }

    @PatchMapping("/password")
    public AuthResponse changePassword(Authentication authentication,
            @Valid @RequestBody AuthRequests.ChangePassword request) {
        return service.changePassword(authentication.getName(), request);
    }

    @PatchMapping("/2fa")
    public AuthResponse.UserResponse updateTwoFactor(Authentication authentication,
            @Valid @RequestBody AuthRequests.UpdateTwoFactor request) {
        return service.updateTwoFactor(authentication.getName(), request);
    }
}
