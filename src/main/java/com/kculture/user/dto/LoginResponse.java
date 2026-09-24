package com.kculture.user.dto;

// 로그인 성공 응답 = 프로필 + 이후 요청에 Authorization: Bearer <token>으로 실어 보낼 토큰
public record LoginResponse(UserResponse user, String token) {
}
