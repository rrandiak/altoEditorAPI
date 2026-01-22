package cz.inovatika.altoEditor.api.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.api.auth.UserContextService;
import cz.inovatika.altoEditor.api.dto.UserDto;
import cz.inovatika.altoEditor.api.mapper.UserMapper;
import cz.inovatika.altoEditor.core.repository.UserRepository;
import cz.inovatika.altoEditor.core.service.UserService;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    private final UserRepository userRepository;

    private final UserContextService userContext;

    private final UserMapper userMapper;

    public Page<UserDto> searchUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toDto);
    }

    public UserDto getCurrentUser() {
        return userRepository.findById(userContext.getUserId()).map(userMapper::toDto)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
    }

    public UserDto createCurrentUser() {
        return userMapper.toDto(userService.createUser(userContext.getUsername()));
    }

}
