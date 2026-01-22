package cz.inovatika.altoEditor.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.core.entity.User;
import cz.inovatika.altoEditor.core.repository.UserRepository;

/**
 * Service layer for User operations.
 * Handles business logic and transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * Create a new user.
     * 
     * @throws IllegalArgumentException if login is null or user already exists
     */
    @Transactional
    public User createUser(String username) {
        if (userRepository.existsByLogin(username)) {
            throw new IllegalArgumentException("User with login '" + username + "' already exists");
        }

        User user = new User();
        user.setLogin(username);
        User saved = userRepository.save(user);
        log.info("Created user with ID: {} and login: {}", saved.getId(), saved.getLogin());
        return saved;
    }
}
