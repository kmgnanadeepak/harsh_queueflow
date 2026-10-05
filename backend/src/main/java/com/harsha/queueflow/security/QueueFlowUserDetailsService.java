package com.harsha.queueflow.security;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads QueueFlow accounts for Spring Security authentication.
 *
 * <p>Accounts are matched on the (unique, case-insensitive) email address, which
 * is the common identity across both the LOCAL and GOOGLE login methods.</p>
 */
@Service
public class QueueFlowUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public QueueFlowUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public QueueFlowPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for this email address"));
        return new QueueFlowPrincipal(user);
    }

    @Transactional(readOnly = true)
    public QueueFlowPrincipal loadById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Account no longer exists"));
        return new QueueFlowPrincipal(user);
    }
}
