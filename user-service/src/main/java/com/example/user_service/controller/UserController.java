package com.example.user_service.controller;

import com.example.user_service.dto.ChangePasswordRequest;
import com.example.user_service.dto.CreateUserRequest;
import com.example.user_service.dto.UpdateProfileRequest;
import com.example.user_service.dto.UserResponse;
import com.example.user_service.service.RedisTestService;
import com.example.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RedisTestService redisTestService;

    @PostMapping
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request
            ) {
        return userService.createUser(request);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication) {
        String email = authentication.getName();

        return userService.getByEmail(email);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
            ) {

        userService.changePassword(
                authentication.getName(),
                request
        );
    }

    @PutMapping("/me")
    public UserResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
            ) {
        return userService.updateProfile(
                authentication.getName(),
                request
        );
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/admin/users")
    public Page<UserResponse> getAllUsers(
            @PageableDefault(
                    size = 20,
                    sort = "id",
                    direction = Sort.Direction.ASC
            )
            Pageable pageable) {
        return userService.getAllUsers(pageable);
    }

    @GetMapping("/debug-auth")
    public String debugAuth() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication.getName()
                + " | "
                + authentication.getAuthorities()
                + " | authenticated="
                + authentication.isAuthenticated()
                + " | class="
                + authentication.getClass().getSimpleName();
    }

    @GetMapping("/debug-authority")
    public Object debugAuthority(Authentication authentication) {
        return authentication.getAuthorities();
    }


    @GetMapping("/redis-test/save")
    public String redisSave() {
        redisTestService.save();

        return "Saved to Redis";
    }

    @GetMapping("/redis-test/get")
    public String redisGet() {
        return redisTestService.get();
    }

    @GetMapping("/redis-test/database")
    public int getDatabase() {
        return redisTestService.getDatabase();
    }


}
