package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.presentation.dto.request.UserSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.CurrentUserDto;
import cz.inovatika.altoEditor.presentation.dto.response.UserDto;
import cz.inovatika.altoEditor.presentation.mapper.CurrentUserMapper;
import cz.inovatika.altoEditor.presentation.mapper.UserMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import cz.inovatika.altoEditor.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Facade for user operations: search users, get/create current user profile.
 */
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    private final UserContextService userContext;

    private final UserMapper userMapper;

    private final CurrentUserMapper currentUserMapper;

    /** Search users with optional filters and Spring pagination. */
    public Page<UserDto> searchUsers(UserSearchRequest request, Pageable pageable) {
        return userService.search(
                request.getIsKramerius(),
                request.getIsEngine(),
                request.getIsEnabled(),
                pageable).map(userMapper::toDto);
    }

    /** Get current authenticated user profile (must exist in DB). */
    public CurrentUserDto getCurrentUser() {
        if (userContext.getUserId() == null) {
            throw new UserNotFoundException(userContext.getUsername());
        }
        return currentUserMapper.toDto(userContext.getCurrentUser());
    }

    /** Create or get local user profile for current authenticated user. */
    public CurrentUserDto createCurrentUser() {
        return currentUserMapper.toDto(userService.createUser(userContext.getUid(), userContext.getUsername()),
                userContext.getCurrentUser());
    }

}
