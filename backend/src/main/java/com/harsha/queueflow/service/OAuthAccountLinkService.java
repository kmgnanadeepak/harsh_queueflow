package com.harsha.queueflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.exception.ForbiddenOperationException;
import com.harsha.queueflow.repository.UserRepository;
import com.harsha.queueflow.security.GoogleOidcUserService;

/**
 * Account linking for federated (Google) logins.
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>The identity is matched on Google's stable subject id. No duplicate is created.</li>
 *   <li>If the subject is unknown but a QueueFlow account already uses the same
 *       (Google verified) email, the Google identity is <em>linked</em> to that
 *       account. The existing password hash, role and status are preserved - they
 *       are never overwritten by the federated login.</li>
 *   <li>If the email is already linked to a <em>different</em> Google subject the
 *       login is rejected instead of hijacking the account.</li>
 *   <li>Otherwise a brand new account is created with role {@link Role#USER}.
 *       Google can never mint a STAFF or ADMIN account.</li>
 * </ol>
 */
@Service
public class OAuthAccountLinkService {

    private static final Logger log = LoggerFactory.getLogger(OAuthAccountLinkService.class);

    private final UserRepository userRepository;

    public OAuthAccountLinkService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User resolveGoogleUser(OAuth2User oauth2User) {
        String providerId = attribute(oauth2User, GoogleOidcUserService.ATTR_SUB);
        String email = normaliseEmail(attribute(oauth2User, GoogleOidcUserService.ATTR_EMAIL));
        String name = attribute(oauth2User, GoogleOidcUserService.ATTR_NAME);
        String picture = attribute(oauth2User, GoogleOidcUserService.ATTR_PICTURE);

        if (providerId == null || email == null) {
            throw new ForbiddenOperationException("Google did not return a usable identity");
        }

        User user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId).orElse(null);
        boolean created = false;

        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        }

        if (user == null) {
            user = newUser(email, name, providerId, picture);
            created = true;
        } else {
            if (!user.isActive()) {
                throw new ForbiddenOperationException("This account has been deactivated. Contact support.");
            }
            if (user.getProvider() == AuthProvider.GOOGLE
                    && user.getProviderId() != null
                    && !user.getProviderId().equals(providerId)) {
                throw new ConflictException(
                        "This email is already linked to a different Google account. Sign in with the original method.");
            }
            linkGoogleIdentity(user, providerId, name, picture);
        }

        User saved = userRepository.save(user);
        log.info("Google login resolved: email={} providerId={} created={} role={}",
                saved.getEmail(), providerId, created, saved.getRole());
        return saved;
    }

    private void linkGoogleIdentity(User user, String providerId, String name, String picture) {
        // Role and password are intentionally left untouched: linking a Google
        // identity must not change what the account is authorised to do.
        user.setProvider(AuthProvider.GOOGLE);
        user.setProviderId(providerId);
        if ((user.getName() == null || user.getName().isBlank()) && name != null) {
            user.setName(name);
        }
        if (picture != null && !picture.isBlank()) {
            user.setProfileImage(picture);
        }
    }

    private User newUser(String email, String name, String providerId, String picture) {
        User user = new User();
        user.setEmail(email);
        user.setName(name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name);
        user.setPassword(null); // Google accounts have no local password.
        user.setProvider(AuthProvider.GOOGLE);
        user.setProviderId(providerId);
        user.setProfileImage(picture);
        user.setRole(Role.USER); // Federated logins can never self-assign a privileged role.
        user.setActive(true);
        return user;
    }

    private String attribute(OAuth2User user, String key) {
        Object value = user.getAttributes().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
