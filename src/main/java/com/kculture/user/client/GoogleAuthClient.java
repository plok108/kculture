package com.kculture.user.client;

import tools.jackson.databind.JsonNode;
import com.kculture.user.client.dto.GoogleUserInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 프론트가 구글 SDK로 받은 액세스 토큰을 구글 서버에 직접 검증한다.
 * providerUid/email/nickname은 클라이언트가 주장한 값이 아니라 이 응답에서만 뽑아 쓴다.
 */
@Component
public class GoogleAuthClient {

    private final RestClient client;

    public GoogleAuthClient(RestClient googleRestClient) {
        this.client = googleRestClient;
    }

    public GoogleUserInfo fetch(String accessToken) {
        JsonNode resp;
        try {
            resp = client.get()
                    .uri("/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 로그인 인증에 실패했습니다.");
        }

        if (resp == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 로그인 인증에 실패했습니다.");
        }

        String providerUid = resp.path("sub").asText(null);
        if (providerUid == null || providerUid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "구글 로그인 인증에 실패했습니다.");
        }

        String email = resp.path("email").asText(null);
        String nickname = resp.path("name").asText(null);

        return new GoogleUserInfo(providerUid, email, nickname);
    }
}
