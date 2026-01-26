package cz.inovatika.altoEditor.presentation.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cz.inovatika.altoEditor.presentation.security.AuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

@TestPropertySource(properties = "altoeditor.home=src/test/resources")
@EnableMethodSecurity(prePostEnabled = true)
class ControllerTest {

    @MockitoBean
    private AuthenticationFilter authFilter;

    @BeforeEach
    void allowAuthenticationFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(authFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }
}