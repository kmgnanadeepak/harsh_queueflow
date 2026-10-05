package com.harsha.queueflow.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Normalises Google's OpenID Connect claims into a Spring Security principal.
 *
 * <p>The default user-info call is delegated to Spring Security, after which only
 * the basic, verified identity attributes are retained: {@code sub} (stable
 * provider id), {@code email}, {@code name} and {@code picture}. No access token,
 * refresh token or granted scope is kept, and QueueFlow issues its own JWT.</p>
 */
@Component
public class GoogleOidcUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String ATTR_SUB = "sub";
    public static final String ATTR_EMAIL = "email";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_PICTURE = "picture";
    public static final String ATTR_EMAIL_VERIFIED = "email_verified";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User user = delegate.loadUser(userRequest);
        Map<String, Object> source = user.getAttributes();

        Map<String, Object> attributes = new HashMap<>();
        putIfPresent(attributes, ATTR_SUB, source.get(ATTR_SUB));
        putIfPresent(attributes, ATTR_EMAIL, source.get(ATTR_EMAIL));
        putIfPresent(attributes, ATTR_NAME, source.get(ATTR_NAME));
        putIfPresent(attributes, ATTR_PICTURE, source.get(ATTR_PICTURE));
        putIfPresent(attributes, ATTR_EMAIL_VERIFIED, source.get(ATTR_EMAIL_VERIFIED));

        if (attributes.get(ATTR_SUB) == null) {
            throw new IllegalStateException("Google did not return a subject identifier");
        }

        Collection<GrantedAuthority> authorities = new LinkedHashSet<>(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        return new DefaultOAuth2User(authorities, attributes, ATTR_SUB);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
