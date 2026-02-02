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
    public User createUser(String uid, String username) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("User with username '" + username + "' already exists");
        }

        User user = new User();
        user.setUid(uid);
        user.setUsername(username);
        User saved = userRepository.save(user);
        log.info("Created user with ID: {} and username: {}", saved.getId(), saved.getUsername());
        return saved;
    }

    /**
     * Get user by id.
     * @param id
     * 
     * @return User entity
     * @throws IllegalArgumentException if user does not exist
     */
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /**
     * Get user by username.
     * 
     * @param username
     * @return User entity
     * @throws IllegalArgumentException if user does not exist
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    /**
     * Get special user by username.
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
