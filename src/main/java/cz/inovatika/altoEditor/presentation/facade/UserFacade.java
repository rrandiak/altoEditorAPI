package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.repository.UserRepository;
import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.presentation.dto.request.UserSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.UserDto;
import cz.inovatika.altoEditor.presentation.mapper.UserMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    private final UserRepository userRepository;

    private final UserContextService userContext;

    private final UserMapper userMapper;

    public Page<UserDto> searchUsers(UserSearchRequest request, Pageable pageable) {
        return userService.search(
            request.getIsKramerius(),
            request.getIsEngine(),
            request.getIsEnabled(),
            pageable
        ).map(userMapper::toDto);
    }

    public UserDto getCurrentUser() {
        return userRepository.findById(userContext.getUserId()).map(userMapper::toDto)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
    }

    public UserDto createCurrentUser() {
        return userMapper.toDto(userService.createUser(userContext.getUid(), userContext.getUsername()));
    }

}
