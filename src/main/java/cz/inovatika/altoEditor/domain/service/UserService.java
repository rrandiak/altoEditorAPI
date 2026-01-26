package cz.inovatika.altoEditor.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.inovatika.altoEditor.domain.enums.SpecialUser;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    /**
     * Get user by login.
     * 
     * @param username
     * @return User entity
     * @throws IllegalArgumentException if user does not exist
     */
    public User getUserByLogin(String username) {
        return userRepository.findByLogin(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    /**
     * Get special user by login.
     * If the user does not exist, an exception is thrown.
     * 
     * @param specialUser type of special user
     * @return User entity
     * @throws IllegalArgumentException if user does not exist
     */
    public User getSpecialUser(SpecialUser specialUser) {
        return userRepository.findSpecialUser(specialUser)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + specialUser));
    }
}
