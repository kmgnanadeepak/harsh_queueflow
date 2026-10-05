package com.harsha.queueflow.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed, environment driven configuration for QueueFlow.
 *
 * <p>Every value is bound from environment variables (or overridden by
 * {@code application-local.properties}, which is git-ignored) so that no secret
 * is ever committed to source control.</p>
 */
@ConfigurationProperties(prefix = "queueflow")
public class QueueFlowProperties {

    private final Jwt jwt = new Jwt();
    private final Frontend frontend = new Frontend();
    private final Queue queue = new Queue();
    private final GoogleOAuth2 google = new GoogleOAuth2();
    private final Seed seed = new Seed();

    public Jwt getJwt() {
        return jwt;
    }

    public Frontend getFrontend() {
        return frontend;
    }

    public Queue getQueue() {
        return queue;
    }

    public GoogleOAuth2 getGoogle() {
        return google;
    }

    public Seed getSeed() {
        return seed;
    }

    public static class Jwt {
        /** HMAC-SHA signing secret. Must be at least 32 characters. */
        private String secret = "";
        /** Access token lifetime. */
        private Duration expiration = Duration.ofHours(8);
        private String issuer = "queueflow";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getExpiration() {
            return expiration;
        }

        public void setExpiration(Duration expiration) {
            this.expiration = expiration;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Frontend {
        /** Public base URL of the React application (used for OAuth redirects and CORS). */
        private String url = "http://localhost:5173";
        /** Route the SPA uses to consume the JWT issued by the OAuth2 success handler. */
        private String oauthCallbackPath = "/auth/callback";
        private final List<String> allowedOrigins = new ArrayList<>();
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getOAuthCallbackPath() {
            return oauthCallbackPath;
        }

        public void setOAuthCallbackPath(String oauthCallbackPath) {
            this.oauthCallbackPath = oauthCallbackPath;
        }

        public List<String> getAllowedOrigins() {
            if (allowedOrigins.isEmpty()) {
                return List.of(url);
            }
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins.clear();
            if (allowedOrigins != null) {
                allowedOrigins.stream()
                        .filter(origin -> origin != null && !origin.isBlank())
                        .forEach(this.allowedOrigins::add);
            }
        }
    }

    public static class Queue {
        /** Minutes of waiting required for one unit of priority aging. */
        private int ageingIntervalMinutes = 15;
        /** Maximum number of rank positions a token may age backwards. */
        private int ageingMaxBoost = 2;
        /** Queue position at (or below) which the "approaching" notification fires. */
        private int approachingThreshold = 2;
        /** How often the approaching-notification scheduler runs. */
        private int approachingScanIntervalMs = 60_000;

        public int getAgeingIntervalMinutes() {
            return ageingIntervalMinutes;
        }

        public void setAgeingIntervalMinutes(int ageingIntervalMinutes) {
            this.ageingIntervalMinutes = ageingIntervalMinutes;
        }

        public int getAgeingMaxBoost() {
            return ageingMaxBoost;
        }

        public void setAgeingMaxBoost(int ageingMaxBoost) {
            this.ageingMaxBoost = ageingMaxBoost;
        }

        public int getApproachingThreshold() {
            return approachingThreshold;
        }

        public void setApproachingThreshold(int approachingThreshold) {
            this.approachingThreshold = approachingThreshold;
        }

        public int getApproachingScanIntervalMs() {
            return approachingScanIntervalMs;
        }

        public void setApproachingScanIntervalMs(int approachingScanIntervalMs) {
            this.approachingScanIntervalMs = approachingScanIntervalMs;
        }
    }

    public static class GoogleOAuth2 {
        private String clientId = "";
        private String clientSecret = "";
        /** Defaults to the Spring Security callback path when left empty. */
        private String redirectUri = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }

    public static class Seed {
        /** Enables the development seed data (admin/staff/user + demo organization). */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
