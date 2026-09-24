package com.kculture.user.client.dto;

// 카카오 사용자 정보 API 응답에서 뽑아온, 서버가 검증한 값.
public record KakaoUserInfo(
        String providerUid,
        String email,
        String nickname
) {
}
