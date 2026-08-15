package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.config.ConciergeProperties;
import com.vorconcierge.ragcore.model.AuthProfile;
import com.vorconcierge.ragcore.repository.UserRepository;
import com.vorconcierge.ragcore.security.JwtService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String cookieName;
    private final int expirationHours;
    private final boolean cookieSecure;
    private final CodeVerifier totpVerifier;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, ConciergeProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cookieName = properties.jwt().cookieName();
        this.expirationHours = properties.jwt().expirationHours();
        this.cookieSecure = properties.jwt().cookieSecure();
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        this.totpVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    }

    public record LoginResult(String username, Long orgId, boolean isPlatformOperator) {}

    public LoginResult login(String email, String password, String totp, String ip, String userAgent,
                             HttpServletResponse response) {
        AuthProfile profile = userRepository.findAuthProfileByEmail(email)
                .orElseThrow(() -> new SecurityException("Invalid credentials"));

        if (!profile.enabled()) {
            throw new SecurityException("Account disabled");
        }
        if (!passwordEncoder.matches(password, profile.passwordHash())) {
            throw new SecurityException("Invalid credentials");
        }
        if (profile.totpSecret() != null && !profile.totpSecret().isBlank()) {
            if (totp == null || totp.isBlank() || !totpVerifier.isValidCode(profile.totpSecret(), totp)) {
                throw new SecurityException("Invalid 2FA code");
            }
        }

        return issueSession(profile, ip, userAgent, response);
    }

    /**
     * Re-mints the JWT from current DB state for the already-authenticated caller. Used after a
     * role/department change that must take effect within the same session, without building a
     * full refresh-token rotation system.
     */
    public LoginResult refresh(Long userId, String ip, String userAgent, HttpServletResponse response) {
        AuthProfile profile = userRepository.findAuthProfileById(userId)
                .orElseThrow(() -> new SecurityException("Account no longer exists"));
        if (!profile.enabled()) {
            throw new SecurityException("Account disabled");
        }
        return issueSession(profile, ip, userAgent, response);
    }

    private LoginResult issueSession(AuthProfile profile, String ip, String userAgent, HttpServletResponse response) {
        String token = jwtService.generateToken(profile);
        var authUser = jwtService.validateToken(token);

        Timestamp expiresAt = Timestamp.from(Instant.now().plus(expirationHours, ChronoUnit.HOURS));
        userRepository.insertSession(profile.orgId(), profile.userId(), authUser.jwtId(), expiresAt, ip, userAgent);

        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(expirationHours * 3600L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return new LoginResult(profile.username(), profile.orgId(), profile.isPlatformOperator());
    }

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
