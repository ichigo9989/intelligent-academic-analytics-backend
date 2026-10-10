package academic_analytics_backend.config;

import academic_analytics_backend.entity.AppUser;
import academic_analytics_backend.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;
    private final AppUserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenService tokenService, AppUserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String email = tokenService.subject(header.substring(7));
                AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
                if (user != null && user.isEnabled()) {
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(
                            user.getEmail(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
