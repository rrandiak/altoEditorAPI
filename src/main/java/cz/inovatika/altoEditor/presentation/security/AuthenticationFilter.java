package cz.inovatika.altoEditor.presentation.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import cz.inovatika.altoEditor.domain.enums.Role;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.ServletException;

/**
 * Validates JWT from {@code Authorization: Bearer <token>}, resolves user via Kramerius,
 * loads local user ID if present, and sets {@link UserProfile} in the security context.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final KrameriusService krameriusService;

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            KrameriusUser user = krameriusService.getUser(token);

            UserProfile profile = new UserProfile(
                    token,
                    userRepository.findByUsername(user.getUsername()).map(u -> u.getId()).orElse(null),
                    user.getUid(),
                    user.getUsername(),
                    user.getRoles());

            List<SimpleGrantedAuthority> authorities = profile.getRoles().stream()
                    .map(Role::toString)
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    profile,
                    token,
                    authorities);

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
