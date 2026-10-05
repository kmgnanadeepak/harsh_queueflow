package com.harsha.queueflow.security;

import java.io.IOException;

import com.harsha.queueflow.config.QueueFlowProperties;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.service.OAuthAccountLinkService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Completes the Google OAuth2 / OIDC flow.
 *
 * <p>Google's identity is resolved into a QueueFlow account (see
 * {@link OAuthAccountLinkService}) and QueueFlow then issues <em>its own</em>
 * JWT, which is handed to the React application through a single-use redirect.
 * The Google access token is discarded and never becomes a QueueFlow token.</p>
 */
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2SuccessHandler.class);

    private final OAuthAccountLinkService accountLinkService;
    private final JwtUtil jwtUtil;
    private final QueueFlowProperties properties;

    public GoogleOAuth2SuccessHandler(OAuthAccountLinkService accountLinkService,
                                      JwtUtil jwtUtil,
                                      QueueFlowProperties properties) {
        this.accountLinkService = accountLinkService;
        this.jwtUtil = jwtUtil;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported authentication type");
            return;
        }
        OAuth2User oauth2User = oauth2Token.getPrincipal();
        User user = accountLinkService.resolveGoogleUser(oauth2User);
        String token = jwtUtil.generateToken(new QueueFlowPrincipal(user));

        String redirect = UriComponentsBuilder
                .fromUriString(trimTrailingSlash(properties.getFrontend().getUrl()))
                .path(properties.getFrontend().getOAuthCallbackPath())
                .queryParam("token", token)
                .build()
                .toUriString();

        log.info("Issued QueueFlow JWT after Google login for userId={} role={}", user.getId(), user.getRole());
        response.sendRedirect(redirect);
    }

    static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
