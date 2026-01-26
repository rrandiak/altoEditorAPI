package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import cz.inovatika.altoEditor.application.facade.UserFacade;
import cz.inovatika.altoEditor.presentation.dto.response.UserDto;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade facade;

    /**
     * Search users with optional filters and pagination.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<UserDto>> getUsers(
            Pageable pageable) {

        Page<UserDto> page = facade.searchUsers(pageable);

        return ResponseEntity.ok(page);
    }

    /**
     * Get the current logged-in user's profile.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<UserDto> getCurrentUser() {

        return ResponseEntity.ok(facade.getCurrentUser());
    }

    /**
     * Create a new user profile for the current logged-in user.
     */
    @PostMapping("/me")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<UserDto> createCurrentUser() {

        return ResponseEntity.ok(facade.createCurrentUser());
    }
}
