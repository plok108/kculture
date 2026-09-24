package com.kculture.user.controller;

import com.kculture.common.auth.CurrentUserId;
import com.kculture.user.dto.SignupRequest;
import com.kculture.user.dto.UserResponse;
import com.kculture.user.dto.UserUpdateRequest;
import com.kculture.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 이메일 회원가입
    @PostMapping("/api/users/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return userService.signup(request);
    }

    // 내 프로필 조회
    @GetMapping("/api/me")
    public UserResponse getMe(@CurrentUserId Long userId) {
        return userService.getUser(userId);
    }

    // 내 설정(닉네임/국적/언어) 수정
    @PatchMapping("/api/me")
    public UserResponse updateMe(@CurrentUserId Long userId,
                                  @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateUser(userId, request);
    }
}
