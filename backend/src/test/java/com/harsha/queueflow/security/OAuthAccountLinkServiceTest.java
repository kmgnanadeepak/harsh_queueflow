package com.harsha.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.AuthorityUtils;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.exception.ForbiddenOperationException;
import com.harsha.queueflow.integration.TestSupport;
import com.harsha.queueflow.service.OAuthAccountLinkService;

/**
 * Google account linking rules.
 *
 * The properties under test are the security ones: a federated login must never
 * escalate privileges, must never hijack an account that is linked to a
 * different Google identity, and must preserve a local password and role when
 * it links to an existing account.
 */
@SpringBootTest
class OAuthAccountLinkServiceTest extends TestSupport {

    @Autowired
    private OAuthAccountLinkService linkService;

    @Test
    @DisplayName("a first Google sign-in creates a plain USER with no password")
    void firstGoogleLoginCreatesCustomer() {
        User user = linkService.resolveGoogleUser(googleUser("sub-1", "New.User@example.com", "New User", null));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("new.user@example.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("sub-1");
        assertThat(user.getPassword()).isNull();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("a Google identity cannot create a STAFF or ADMIN account")
    void googleCannotCreatePrivilegedAccount() {
        linkService.resolveGoogleUser(googleUser("sub-escalate", "sneaky@example.com", "Sneaky", null));

        User stored = userRepository.findByEmailIgnoreCase("sneaky@example.com").orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("signing in again with the same subject reuses the same account")
    void repeatLoginDoesNotDuplicateTheAccount() {
        User first = linkService.resolveGoogleUser(googleUser("sub-2", "repeat@example.com", "Repeat", null));
        User second = linkService.resolveGoogleUser(googleUser("sub-2", "repeat@example.com", "Repeat", null));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a Google login links to an existing local account and keeps its password, role and id")
    void linksToExistingLocalAccount() {
        User local = createUser("Linked.User@example.com", "Password123", Role.STAFF);
        String originalHash = local.getPassword();

        User linked = linkService.resolveGoogleUser(
                googleUser("sub-3", "linked.user@example.com", "Ignored Name", "https://cdn/pic.png"));

        assertThat(linked.getId()).isEqualTo(local.getId());
        assertThat(linked.getRole()).isEqualTo(Role.STAFF);
        assertThat(linked.getPassword()).isEqualTo(originalHash);
        assertThat(linked.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(linked.getProviderId()).isEqualTo("sub-3");
        assertThat(linked.getProfileImage()).isEqualTo("https://cdn/pic.png");
        // An existing display name is not overwritten by the Google profile name.
        assertThat(linked.getName()).isNotEqualTo("Ignored Name");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a linked local account can still sign in with its password")
    void linkingDoesNotBreakPasswordLogin() {
        User local = createUser("both@example.com", "Password123", Role.USER);
        linkService.resolveGoogleUser(googleUser("sub-4", "both@example.com", null, null));

        User afterLink = userRepository.findById(local.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("Password123", afterLink.getPassword())).isTrue();
    }

    @Test
    @DisplayName("an email already linked to a different Google subject is refused, not hijacked")
    void refusesToHijackAnEmailLinkedToAnotherGoogleAccount() {
        linkService.resolveGoogleUser(googleUser("sub-first", "owned@example.com", "Owner", null));

        assertThatThrownBy(() ->
                linkService.resolveGoogleUser(googleUser("sub-second", "owned@example.com", "Attacker", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked to a different Google account");

        // The original owner must be untouched.
        User stored = userRepository.findByEmailIgnoreCase("owned@example.com").orElseThrow();
        assertThat(stored.getProviderId()).isEqualTo("sub-first");
        assertThat(stored.getName()).isEqualTo("Owner");
    }

    @Test
    @DisplayName("a deactivated account cannot be reached through Google either")
    void deactivatedAccountCannotUseGoogleLogin() {
        User local = createUser("gone@example.com", Role.USER);
        local.setActive(false);
        userRepository.saveAndFlush(local);

        assertThatThrownBy(() ->
                linkService.resolveGoogleUser(googleUser("sub-5", "gone@example.com", "Gone", null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    @DisplayName("an identity without a subject id is rejected")
    void requiresProviderSubject() {
        assertThatThrownBy(() -> linkService.resolveGoogleUser(googleUser(null, "no-sub@example.com", "No Sub", null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("usable identity");
    }

    @Test
    @DisplayName("an identity without an email is rejected")
    void requiresEmail() {
        assertThatThrownBy(() -> linkService.resolveGoogleUser(googleUser("sub-6", null, "No Email", null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("usable identity");
    }

    @Test
    @DisplayName("email matching is case insensitive")
    void emailMatchingIsCaseInsensitive() {
        User local = createUser("Mixed.Case@example.com", Role.ADMIN);

        User linked = linkService.resolveGoogleUser(
                googleUser("sub-7", "MIXED.CASE@EXAMPLE.COM", null, null));

        assertThat(linked.getId()).isEqualTo(local.getId());
        assertThat(linked.getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the resolved account is turned into a QueueFlow principal carrying its real role")
    void resolvedUserBecomesAPrincipal() {
        User local = createUser("principal@example.com", Role.STAFF);

        User linked = linkService.resolveGoogleUser(
                googleUser("sub-8", "principal@example.com", null, null));
        QueueFlowPrincipal principal = new QueueFlowPrincipal(linked);

        assertThat(principal.getId()).isEqualTo(local.getId());
        assertThat(principal.getRole()).isEqualTo(Role.STAFF);
        assertThat(principal.getAuthorities()).extracting("authority")
                .contains("ROLE_STAFF");
    }

    private OAuth2User googleUser(String sub, String email, String name, String picture) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (sub != null) {
            attributes.put(GoogleOidcUserService.ATTR_SUB, sub);
        }
        if (email != null) {
            attributes.put(GoogleOidcUserService.ATTR_EMAIL, email);
            attributes.put(GoogleOidcUserService.ATTR_EMAIL_VERIFIED, Boolean.TRUE);
        }
        if (name != null) {
            attributes.put(GoogleOidcUserService.ATTR_NAME, name);
        }
        if (picture != null) {
            attributes.put(GoogleOidcUserService.ATTR_PICTURE, picture);
        }

        // DefaultOAuth2User requires its name-attribute key to exist, so when the
        // "sub" claim is deliberately absent a placeholder is used instead. That
        // keeps the fixture honest about a claim-less identity.
        String nameAttributeKey = attributes.containsKey(GoogleOidcUserService.ATTR_SUB)
                ? GoogleOidcUserService.ATTR_SUB
                : "missing-sub-placeholder";
        attributes.put(nameAttributeKey, attributes.getOrDefault(nameAttributeKey, "placeholder"));

        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("ROLE_USER"), attributes,
                nameAttributeKey);
    }
}
