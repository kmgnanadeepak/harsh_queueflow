package com.harsha.queueflow.service;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.AuthResponse;
import com.harsha.queueflow.dto.GoogleAuthStatusResponse;
import com.harsha.queueflow.dto.LoginRequest;
import com.harsha.queueflow.dto.RegisterRequest;
import com.harsha.queueflow.dto.UserResponse;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.repository.UserRepository;
import com.harsha.queueflow.security.CurrentUser;
import com.harsha.queueflow.security.JwtUtil;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.security.QueueFlowUserDetailsService;

/**
 * Email + password authentication.
 *
 * <p>Public registration can only ever produce a {@link Role#USER}: the request
 * DTO has no role field, and the service hard-codes the role before saving.
 * Passwords are hashed with BCrypt and never returned by the API.</p>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final QueueFlowUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       QueueFlowUserDetailsService userDetailsService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(request.phone().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER); // Fixed: a public registration can never escalate privileges.
        user.setActive(true);

        User saved = userRepository.save(user);
        QueueFlowPrincipal principal = new QueueFlowPrincipal(saved);
        return new AuthResponse(jwtUtil.generateToken(principal), UserResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalise(request.email());
        QueueFlowPrincipal principal = userDetailsService.loadUserByUsername(email);

        if (principal.getPassword() == null) {
            // Federated-only account: reject password login instead of a misleading
            // "wrong password" error.
            throw new BadCredentialsException(
                    "This account signs in with Google. Use 'Continue with Google'.");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (DisabledException ex) {
            throw new DisabledException("This account has been deactivated");
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new AuthResponse(jwtUtil.generateToken(principal), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        Long id = CurrentUser.id();
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("Account no longer exists"));
    }

    public GoogleAuthStatusResponse googleStatus(boolean configured) {
        return new GoogleAuthStatusResponse(configured,
                configured ? "/oauth2/authorization/google" : null,
                configured ? "/login/oauth2/code/google" : null);
    }

    private String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
