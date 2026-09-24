package com.kculture.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부 API 호출용 RestClient 빈.
 * RestClient는 spring-web(webmvc 스타터에 포함)에 있으므로 별도 의존성이 필요 없다.
 * JVM 프록시 설정은 실행 환경의 시스템 프로퍼티로 적용된다.
 */
@Configuration
public class HttpClientConfig {

    // Gemini (generativelanguage) 전용
    @Bean
    public RestClient geminiRestClient() {
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    // YouTube Data API v3 전용
    @Bean
    public RestClient youtubeRestClient() {
        return RestClient.builder()
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }

    // 카카오 로그인 액세스 토큰 검증(사용자 정보 조회) 전용
    @Bean
    public RestClient kakaoRestClient() {
        return RestClient.builder()
                .baseUrl("https://kapi.kakao.com")
                .build();
    }

    // 구글 로그인 액세스 토큰 검증(사용자 정보 조회) 전용
    @Bean
    public RestClient googleRestClient() {
        return RestClient.builder()
                .baseUrl("https://www.googleapis.com")
                .build();
    }
}
