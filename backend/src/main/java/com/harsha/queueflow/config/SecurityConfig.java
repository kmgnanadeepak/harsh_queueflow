package com.harsha.queueflow.config;

import java.util.List;

import com.harsha.queueflow.security.GoogleOAuth2SuccessHandler;
import com.harsha.queueflow.security.GoogleOidcUserService;
import com.harsha.queueflow.security.JwtAuthenticationFilter;
import com.harsha.queueflow.security.QueueFlowUserDetailsService;
import com.harsha.queueflow.security.RestErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration.
 *
 * <p>Two filter chains are used:</p>
 * <ol>
 *   <li><b>{@code /api/**}</b> - fully stateless, JWT authenticated, JSON error
 *       responses, no CSRF token required (the browser never sends cookies).</li>
 *   <li><b>browser facing endpoints</b> - the OAuth2 login flow, which needs a
 *       session to store the authorization request, plus the WebSocket handshake.</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final QueueFlowProperties properties;
    private final QueueFlowUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestErrorHandlers restErrorHandlers;
    private final GoogleOAuth2SuccessHandler googleSuccessHandler;
    private final GoogleOidcUserService googleOidcUserService;

    public SecurityConfig(QueueFlowProperties properties,
                          QueueFlowUserDetailsService userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestErrorHandlers restErrorHandlers,
                          GoogleOAuth2SuccessHandler googleSuccessHandler,
                          GoogleOidcUserService googleOidcUserService) {
        this.properties = properties;
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restErrorHandlers = restErrorHandlers;
        this.googleSuccessHandler = googleSuccessHandler;
        this.googleOidcUserService = googleOidcUserService;
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restErrorHandlers)
                        .accessDeniedHandler(restErrorHandlers))
                .authorizeHttpRequests(auth -> auth
                        // ---- public authentication endpoints ----
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/google/status").permitAll()
                        // ---- public catalogue (browsable without a token) ----
                        .requestMatchers(HttpMethod.GET, "/api/organizations/active", "/api/services/active").permitAll()
                        // ---- administration ----
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // ---- staff operations (staff + admin) ----
                        .requestMatchers("/api/staff/**").hasAnyRole("STAFF", "ADMIN")
                        // ---- queue insight: any authenticated role, filtered in the service layer ----
                        .requestMatchers("/api/queue/**").authenticated()
                        // ---- user scoped token endpoints (ownership enforced server side) ----
                        .requestMatchers("/api/tokens/my/**", "/api/tokens/history").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/tokens").hasRole("USER")
                        .requestMatchers("/api/tokens/**").hasRole("USER")
                        // ---- notifications ----
                        .requestMatchers("/api/notifications/**").authenticated()
                        // ---- authenticated session endpoints ----
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/profile").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, BasicAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher browserEndpoints = new OrRequestMatcher(
                new AntPathRequestMatcher("/oauth2/**"),
                new AntPathRequestMatcher("/login/**"),
                new AntPathRequestMatcher("/ws/**"),
                new AntPathRequestMatcher("/h2-console/**"),
                new AntPathRequestMatcher("/error"));

        http
                .securityMatcher(browserEndpoints)
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        new AntPathRequestMatcher("/ws/**"),
                        new AntPathRequestMatcher("/h2-console/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/h2-console/**").permitAll()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google"),
                        new OrRequestMatcher(new AntPathRequestMatcher("/oauth2/**"),
                                new AntPathRequestMatcher("/login/**"))))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(googleOidcUserService))
                        .successHandler(googleSuccessHandler)
                        .failureHandler(oauth2FailureHandler()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    public SimpleUrlAuthenticationFailureHandler oauth2FailureHandler() {
        SimpleUrlAuthenticationFailureHandler handler =
                new SimpleUrlAuthenticationFailureHandler(properties.getFrontend().getUrl() + "/login");
        // Keep the reason in the URL for a friendly message, without leaking internals.
        handler.setAllowSessionCreation(false);
        return handler;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getFrontend().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Registers Google as an OAuth2 client when credentials are present. When they
     * are absent the application still boots (handy for local development and CI)
     * and the Google login button reports that the provider is disabled.
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        QueueFlowProperties.GoogleOAuth2 google = properties.getGoogle();
        if (isBlank(google.getClientId()) || isBlank(google.getClientSecret())) {
            return new DisabledClientRegistrationRepository();
        }
        String redirectUri = isBlank(google.getRedirectUri())
                ? "{baseUrl}/login/oauth2/code/{registrationId}"
                : google.getRedirectUri();

        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(google.getClientId())
                .clientSecret(google.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();

        return new InMemoryClientRegistrationRepository(registration);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Fallback repository used when Google credentials are not configured. */
    static final class DisabledClientRegistrationRepository implements ClientRegistrationRepository {
        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return null;
        }
    }
}
