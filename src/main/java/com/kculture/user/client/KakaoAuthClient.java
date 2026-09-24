package com.kculture.user.client;

import tools.jackson.databind.JsonNode;
import com.kculture.user.client.dto.KakaoUserInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 프론트가 카카오 SDK로 받은 액세스 토큰을 카카오 서버에 직접 검증한다.
 * providerUid/email/nickname은 클라이언트가 주장한 값이 아니라 이 응답에서만 뽑아 쓴다.
 */
@Component
public class KakaoAuthClient {

    private final RestClient client;

    public KakaoAuthClient(RestClient kakaoRestClient) {
        this.client = kakaoRestClient;
    }

    public KakaoUserInfo fetch(String accessToken) {
        JsonNode resp;
        try {
            resp = client.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 로그인 인증에 실패했습니다.");
        }

        if (resp == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 로그인 인증에 실패했습니다.");
        }

        String providerUid = resp.path("id").asText(null);
        if (providerUid == null || providerUid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 로그인 인증에 실패했습니다.");
        }

        JsonNode account = resp.path("kakao_account");
        String email = account.path("email").asText(null);
        String nickname = account.path("profile").path("nickname").asText(null);
        if (nickname == null) {
            nickname = resp.path("properties").path("nickname").asText(null);
        }

        return new KakaoUserInfo(providerUid, email, nickname);
    }
}
