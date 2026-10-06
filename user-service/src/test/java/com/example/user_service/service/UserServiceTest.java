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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUserSuccessfully() {

        CreateUserRequest request =
                new CreateUserRequest(
                        "test@gmail.com",
                        "Password123",
                        "Adem",
                        "Orucov"
                );

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("hashed-password")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        UserResponse expectedResponse =
                new UserResponse(
                        1L,
                        "test@gmail.com",
                        "Adem",
                        "Orucov",
                        "CUSTOMER"
                );

        when(userRepository.existsByEmail("test@gmail.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password123"))
                .thenReturn("hashed-password");

        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        when(userMapper.toResponse(user))
                .thenReturn(expectedResponse);

        UserResponse result =
                userService.createUser(request);

        assertNotNull(result);
        assertEquals(expectedResponse, result);

        verify(userRepository)
                .existsByEmail("test@gmail.com");

        verify(passwordEncoder)
                .encode("Password123");

        verify(userRepository)
                .save(any(User.class));

        verify(userMapper)
                .toResponse(user);

    }

    void shouldThrowExceptionWhenEmailAlreadyExists() {

        CreateUserRequest request =
                new CreateUserRequest(
                        "test@gmail.com",
                        "Password123",
                        "Adem",
                        "Orucov"
                );

        when(userRepository.existsByEmail("test@gmail.com"))
                .thenReturn(true);

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.createUser(request)
        );

        verify(userRepository)
                .existsByEmail("test@gmail.com");

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(userRepository, never())
                .save(any(User.class));

        verifyNoInteractions(
                userMapper,
                refreshTokenService
        );
    }

    @Test
    void shouldEncodePasswordBeforeSavingUser() {

        CreateUserRequest request =
                new CreateUserRequest(
                        "test@gmail.com",
                        "Password123",
                        "Adem",
                        "Orucov"
                );

        when(userRepository.existsByEmail("test@gmail.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password123"))
                .thenReturn("hashed-password");

        User savedUser = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("hashed-password")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        UserResponse response =
                new UserResponse(
                        1L,
                        "test@gmail.com",
                        "Adem",
                        "Orucov",
                        "CUSTOMER"
                );

        when(userMapper.toResponse(savedUser))
                .thenReturn(response);

        userService.createUser(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertEquals(
                "hashed-password",
                capturedUser.getPasswordHash()
        );

        assertNotEquals(
                "Password123",
                capturedUser.getPasswordHash()
        );
    }

    @Test
    void shouldChangePasswordSuccessfully() {

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("old-hash")
                .build();

        ChangePasswordRequest request =
                new ChangePasswordRequest(
                        "oldPassword",
                        "newPassword"
                );

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "oldPassword",
                "old-hash"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "newPassword",
                "old-hash"
        )).thenReturn(false);

        when(passwordEncoder.encode("newPassword"))
                .thenReturn("new-hash");

        userService.changePassword(
                "test@gmail.com",
                request
        );

        assertEquals("new-hash", user.getPasswordHash());

        verify(passwordEncoder)
                .encode("newPassword");

        verify(refreshTokenService)
                .revokeAllUserSessions(1L);
    }

    @Test
    void shouldThrowExceptionWhenCurrentPasswordIsIncorrect() {

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("old-hash")
                .build();

        ChangePasswordRequest request =
                new ChangePasswordRequest(
                        "wrongPassword",
                        "newPassword"
                );

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrongPassword",
                "old-hash"
        )).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.changePassword(
                        "test@gmail.com",
                        request
                )
        );

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(refreshTokenService, never())
                .revokeAllUserSessions(anyLong());
    }

    @Test
    void shouldThrowExceptionWhenNewPasswordIsSameAsCurrent() {

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .passwordHash("old-hash")
                .build();

        ChangePasswordRequest request =
                new ChangePasswordRequest(
                        "oldPassword",
                        "oldPassword"
                );

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "oldPassword",
                "old-hash"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "oldPassword",
                "old-hash"
        )).thenReturn(true);

        assertThrows(
                SamePasswordException.class,
                () -> userService.changePassword(
                        "test@gmail.com",
                        request
                )
        );

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(refreshTokenService, never())
                .revokeAllUserSessions(anyLong());
    }

    @Test
    void shouldThrowUserNotFoundWhenChangingPassword() {

        ChangePasswordRequest request =
                new ChangePasswordRequest(
                        "oldPassword",
                        "newPassword"
                );

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.changePassword(
                        "test@gmail.com",
                        request
                )
        );

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void shouldGetUserByEmailSuccessfully() {

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .firstName("Adem")
                .lastName("Orucov")
                .role("CUSTOMER")
                .build();

        UserResponse response = new UserResponse(
                1L,
                "test@gmail.com",
                "Adem",
                "Orucov",
                "CUSTOMER"
        );

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenReturn(response);

        UserResponse result =
                userService.getByEmail("test@gmail.com");

        assertEquals(response, result);

        verify(userRepository)
                .findByEmail("test@gmail.com");

        verify(userMapper)
                .toResponse(user);
    }

    @Test
    void shouldThrowUserNotFoundWhenGettingByEmail() {

        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getByEmail("unknown@gmail.com")
        );

        verify(userMapper, never())
                .toResponse(any(User.class));
    }

    @Test
    void shouldUpdateProfileSuccessfully() {

        User user = User.builder()
                .id(1L)
                .email("test@gmail.com")
                .firstName("Old")
                .lastName("Name")
                .role("CUSTOMER")
                .build();

        UpdateProfileRequest request =
                new UpdateProfileRequest(
                        "NewFirstName",
                        "NewLastName"
                );

        UserResponse response = new UserResponse(
                1L,
                "test@gmail.com",
                "NewFirstName",
                "NewLastName",
                "CUSTOMER"
        );

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenReturn(response);

        UserResponse result =
                userService.updateProfile(
                        "test@gmail.com",
                        request
                );

        assertEquals("NewFirstName", user.getFirstName());
        assertEquals("NewLastName", user.getLastName());

        assertEquals(response, result);

        verify(userMapper)
                .toResponse(user);
    }

    @Test
    void shouldThrowUserNotFoundWhenUpdatingProfile() {

        UpdateProfileRequest request =
                new UpdateProfileRequest(
                        "NewName",
                        "NewLastName"
                );

        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.updateProfile(
                        "unknown@gmail.com",
                        request
                )
        );

        verify(userMapper, never())
                .toResponse(any(User.class));
    }

    @Test
    void shouldGetAllUsersWithPagination() {

        User user1 = User.builder()
                .id(1L)
                .email("user1@gmail.com")
                .firstName("User1")
                .lastName("Test")
                .role("CUSTOMER")
                .build();

        User user2 = User.builder()
                .id(2L)
                .email("user2@gmail.com")
                .firstName("User2")
                .lastName("Test")
                .role("CUSTOMER")
                .build();

        UserResponse response1 = new UserResponse(
                1L,
                "user1@gmail.com",
                "User1",
                "Test",
                "CUSTOMER"
        );

        UserResponse response2 = new UserResponse(
                2L,
                "user2@gmail.com",
                "User2",
                "Test",
                "CUSTOMER"
        );

        Pageable pageable =
                PageRequest.of(0, 2);

        Page<User> userPage =
                new PageImpl<>(
                        List.of(user1, user2),
                        pageable,
                        5
                );

        when(userRepository.findAll(pageable))
                .thenReturn(userPage);

        when(userMapper.toResponse(user1))
                .thenReturn(response1);

        when(userMapper.toResponse(user2))
                .thenReturn(response2);

        Page<UserResponse> result =
                userService.getAllUsers(pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(5, result.getTotalElements());

        assertEquals(response1, result.getContent().get(0));
        assertEquals(response2, result.getContent().get(1));

        verify(userRepository)
                .findAll(pageable);

        verify(userMapper)
                .toResponse(user1);

        verify(userMapper)
                .toResponse(user2);
    }

}
