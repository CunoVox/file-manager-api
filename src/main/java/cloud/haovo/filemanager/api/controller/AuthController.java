package cloud.haovo.filemanager.api.controller;

import cloud.haovo.filemanager.api.request.ChangePasswordRequest;
import cloud.haovo.filemanager.api.request.ForgotPasswordRequest;
import cloud.haovo.filemanager.api.request.LoginRequest;
import cloud.haovo.filemanager.api.request.RefreshTokenRequest;
import cloud.haovo.filemanager.api.request.RegisterRequest;
import cloud.haovo.filemanager.api.request.ResendVerificationRequest;
import cloud.haovo.filemanager.api.request.ResetPasswordRequest;
import cloud.haovo.filemanager.api.request.UpdateProfileRequest;
import cloud.haovo.filemanager.api.request.UpdateTwoFactorRequest;
import cloud.haovo.filemanager.api.request.VerifyEmailRequest;
import cloud.haovo.filemanager.api.request.VerifyTwoFactorRequest;
import cloud.haovo.filemanager.api.response.AuthResponse;
import cloud.haovo.filemanager.service.AuthService;
import cloud.haovo.filemanager.service.UserQuotaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/2fa/verify")
    public AuthResponse verifyTwoFactor(@Valid @RequestBody VerifyTwoFactorRequest request) {
        return service.verifyTwoFactor(request);
    }

    @PostMapping("/email/verify")
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        service.verifyEmail(request);
    }

    @PostMapping("/email/resend-verification")
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        service.resendVerification(request);
    }

    @PostMapping("/password/forgot")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        service.forgotPassword(request);
    }

    @PostMapping("/password/reset")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request);
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
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
            @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(authentication.getName(), request);
    }

    @PatchMapping("/password")
    public AuthResponse changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        return service.changePassword(authentication.getName(), request);
    }

    @PatchMapping("/2fa")
    public AuthResponse.UserResponse updateTwoFactor(Authentication authentication,
            @Valid @RequestBody UpdateTwoFactorRequest request) {
        return service.updateTwoFactor(authentication.getName(), request);
    }
}
