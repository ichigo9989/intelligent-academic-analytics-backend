package academic_analytics_backend.service;

import academic_analytics_backend.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final AuthService authService;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(AppUserRepository userRepository, AuthService authService,
                                @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
                                @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.email = email.trim();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty()) return;
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("Set both bootstrap administrator environment variables.");
        }
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be 12 to 72 characters.");
        }
        if (!userRepository.existsByEmailIgnoreCase(email)) {
            authService.createBootstrapAdmin(email, password);
        }
    }
}
