package com.kculture.user.dto;

import jakarta.validation.constraints.NotBlank;

// 소셜 로그인 요청. providerUid/email/nickname은 클라이언트가 주장하는 값을 받지 않는다 —
// accessToken을 서버가 provider(카카오/구글) API로 직접 검증해서 얻은 값만 신뢰한다.
public record SocialLoginRequest(
        @NotBlank String provider,      // KAKAO / GOOGLE
        @NotBlank String accessToken    // 프론트가 카카오/구글 SDK로 로그인해서 받은 액세스 토큰
) {
}
