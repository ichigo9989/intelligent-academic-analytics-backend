package academic_analytics_backend.service;

import academic_analytics_backend.entity.AppUser;
import academic_analytics_backend.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public DatabaseUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser account = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .disabled(!account.isEnabled())
                .build();
    }
}
