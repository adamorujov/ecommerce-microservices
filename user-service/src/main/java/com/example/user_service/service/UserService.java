package com.example.user_service.service;

import com.example.user_service.dto.ChangePasswordRequest;
import com.example.user_service.dto.CreateUserRequest;
import com.example.user_service.dto.UpdateProfileRequest;
import com.example.user_service.dto.UserResponse;
import com.example.user_service.entity.User;
import com.example.user_service.exception.InvalidCredentialsException;
import com.example.user_service.exception.SamePasswordException;
import com.example.user_service.exception.UserAlreadyExistsException;
import com.example.user_service.exception.UserNotFoundException;
import com.example.user_service.mapper.UserMapper;
import com.example.user_service.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserResponse createUser(CreateUserRequest request) {

        log.info(
                "Create user with email = {}",
                request.email()
        );



        if (userRepository.existsByEmail(request.email())) {
            log.warn(
                    "User registration failed: email already exists, email = {}",
                    request.email()
            );
            throw new UserAlreadyExistsException(
                    "User with email already exists."
            );
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role("CUSTOMER")
                .build();

        User savedUser = userRepository.save(user);

        log.info(
                "User created successfully: userId = {}",
                savedUser.getId()
        );

        return userMapper.toResponse(savedUser);
    }

    public UserResponse getByEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found")
                );

        return userMapper.toResponse(user);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable) {

        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    @Transactional
    public void changePassword(
            String email,
            ChangePasswordRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        boolean currentPasswordMatches =
                passwordEncoder.matches(
                        request.currentPassword(),
                        user.getPasswordHash()
                );

        if (!currentPasswordMatches) {
            throw new InvalidCredentialsException(
                    "Current password is incorrect"
            );
        }

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash()
        )) {
            throw new SamePasswordException(
                    "New password must be different from current password"
            );
        }

        String newPasswordHash = passwordEncoder.encode(
                request.newPassword()
        );

        user.setPasswordHash(newPasswordHash);

        refreshTokenService.revokeAllUserSessions(
                user.getId()
        );

    }

    @Transactional
    public UserResponse updateProfile(
            String email, UpdateProfileRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found."));

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        return userMapper.toResponse(user);
    }
}