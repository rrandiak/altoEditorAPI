package cz.inovatika.altoEditor.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.inovatika.altoEditor.presentation.dto.request.UserSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.UserDto;
import cz.inovatika.altoEditor.presentation.facade.UserFacade;
import lombok.RequiredArgsConstructor;

/**
 * REST API for user management: search users and current user profile (get/create).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade facade;

    /**
     * Search users with optional filters and pagination.
     *
     * @param request  Optional filters (isKramerius, isEngine, isEnabled) via query params.
     * @param pageable Standard Spring pagination (page, size, sort).
     * @return Paginated list of user DTOs.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<Page<UserDto>> getUsers(
            @ModelAttribute UserSearchRequest request,
            Pageable pageable) {

        Page<UserDto> page = facade.searchUsers(request, pageable);

        return ResponseEntity.ok(page);
    }

    /**
     * Get the current authenticated user's profile.
     *
     * @return Current user DTO.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<UserDto> getCurrentUser() {

        return ResponseEntity.ok(facade.getCurrentUser());
    }

    /**
     * Create or ensure a user profile exists for the current authenticated user (e.g. after first login).
     *
     * @return Created or existing user DTO.
     */
    @PostMapping("/me")
    @PreAuthorize("hasAuthority('EDITOR') or hasAuthority('CURATOR')")
    public ResponseEntity<UserDto> createCurrentUser() {

        return ResponseEntity.ok(facade.createCurrentUser());
    }
}
