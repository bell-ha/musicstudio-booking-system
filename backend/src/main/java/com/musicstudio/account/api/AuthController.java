package com.musicstudio.account.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.musicstudio.account.application.AccountService;
import com.musicstudio.account.domain.UserAccount;
import com.musicstudio.common.security.AccessTokens;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AccountService accountService;

    AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse signup(@Valid @RequestBody SignupRequest request) {
        UserAccount account = accountService.signup(request.email(), request.password(), request.name());
        return new AccountResponse(account.getId(), account.getEmail(), account.getName());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AccessTokens.Issued issued = accountService.login(request.email(), request.password());
        return new TokenResponse(issued.accessToken(), issued.expiresAt());
    }

    // bcrypt는 72바이트까지만 쓰므로 비밀번호 길이를 거기서 자른다.
    record SignupRequest(@NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(min = 8, max = 72) String password,
                         @NotBlank @Size(max = 50) String name) {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record AccountResponse(Long id, String email, String name) {
    }

    record TokenResponse(String accessToken, Instant expiresAt) {
    }
}
