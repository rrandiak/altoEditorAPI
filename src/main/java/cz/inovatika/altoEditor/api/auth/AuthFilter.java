package cz.inovatika.altoEditor.api.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import cz.inovatika.altoEditor.core.enums.Role;
import cz.inovatika.altoEditor.core.repository.UserRepository;
import cz.inovatika.altoEditor.kramerius.KrameriusAuthClient;
import cz.inovatika.altoEditor.kramerius.domain.KrameriusUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.ServletException;

// TODO: Add caching of user info to reduce calls to Kramerius
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final KrameriusAuthClient authClient;

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

            try {
                KrameriusUser user = authClient.getUser(token);

                UserProfile profile = new UserProfile(
                        token,
                        userRepository.findByLogin(user.getUsername()).orElse(null).getId(),
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
            } catch (Exception e) {
                // TODO: log and exception
                // invalid token → ignore, user stays unauthenticated
            }
        }

        filterChain.doFilter(request, response);
    }
}
