package com.harsha.queueflow.config;

import com.harsha.queueflow.repository.QueueTokenRepository;
import com.harsha.queueflow.security.JwtUtil;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.security.QueueFlowUserDetailsService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * <p>Secured in two layers:</p>
 * <ol>
 *   <li>On {@code CONNECT} the {@code Authorization: Bearer <jwt>} native header
 *       is validated exactly like an HTTP request. Anonymous sockets are rejected.</li>
 *   <li>On {@code SUBSCRIBE} the destination is authorised against the principal:
 *       a user may only follow their own token and notification topics, while
 *       STAFF/ADMIN may follow any queue topic.</li>
 * </ol>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final String QUEUE_PREFIX = "/topic/queue/";
    private static final String TOKEN_PREFIX = "/topic/token/";
    private static final String NOTIFICATION_PREFIX = "/topic/notifications/";

    private final JwtUtil jwtUtil;
    private final QueueFlowUserDetailsService userDetailsService;
    private final QueueTokenRepository tokenRepository;
    private final QueueFlowProperties properties;

    public WebSocketConfig(JwtUtil jwtUtil,
                           QueueFlowUserDetailsService userDetailsService,
                           QueueTokenRepository tokenRepository,
                           QueueFlowProperties properties) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.tokenRepository = tokenRepository;
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = properties.getFrontend().getAllowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new AuthChannelInterceptor());
    }

    private final class AuthChannelInterceptor implements ChannelInterceptor {

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null) {
                return message;
            }
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                QueueFlowPrincipal principal = authenticate(accessor);
                if (principal == null) {
                    log.debug("Rejected unauthenticated STOMP CONNECT");
                    throw new MessagingException("Unauthorized STOMP connection");
                }
                accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null,
                        principal.getAuthorities()));
                return message;
            }
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                QueueFlowPrincipal principal = principalOf(accessor.getUser());
                String destination = accessor.getDestination();
                if (principal == null || !isSubscriptionAllowed(destination, principal)) {
                    log.warn("Rejected STOMP subscription to {} for {}", destination,
                            principal == null ? "anonymous" : principal.getEmail());
                    throw new MessagingException("Subscription not permitted");
                }
            }
            return message;
        }

        private QueueFlowPrincipal authenticate(StompHeaderAccessor accessor) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return null;
            }
            Claims claims = jwtUtil.parseClaims(header.substring("Bearer ".length()).trim());
            if (claims == null) {
                return null;
            }
            Long userId = jwtUtil.extractUserId(claims);
            if (userId == null) {
                return null;
            }
            try {
                QueueFlowPrincipal principal = userDetailsService.loadById(userId);
                return principal.isEnabled() ? principal : null;
            } catch (RuntimeException ex) {
                return null;
            }
        }

        private QueueFlowPrincipal principalOf(Object user) {
            if (user instanceof QueueFlowPrincipal direct) {
                return direct;
            }
            if (user instanceof UsernamePasswordAuthenticationToken authentication
                    && authentication.getPrincipal() instanceof QueueFlowPrincipal principal) {
                return principal;
            }
            return null;
        }

        private boolean isSubscriptionAllowed(String destination, QueueFlowPrincipal principal) {
            if (destination == null) {
                return false;
            }
            boolean privileged = principal.getRole().isPrivileged();
            if (destination.startsWith(QUEUE_PREFIX)) {
                // Aggregate queue state only - no customer identity is exposed.
                return true;
            }
            if (destination.startsWith(TOKEN_PREFIX)) {
                Long tokenId = parseId(destination.substring(TOKEN_PREFIX.length()));
                return privileged || isTokenOwner(tokenId, principal.getId());
            }
            if (destination.startsWith(NOTIFICATION_PREFIX)) {
                Long userId = parseId(destination.substring(NOTIFICATION_PREFIX.length()));
                return privileged || (userId != null && userId.equals(principal.getId()));
            }
            return false;
        }

        private boolean isTokenOwner(Long tokenId, Long userId) {
            if (tokenId == null) {
                return false;
            }
            try {
                return tokenRepository.findByIdAndUserId(tokenId, userId).isPresent();
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private Long parseId(String raw) {
            String value = raw.startsWith("/") ? raw.substring(1) : raw;
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
