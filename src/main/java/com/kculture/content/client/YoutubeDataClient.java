package com.kculture.content.client;

import tools.jackson.databind.JsonNode;
import com.kculture.content.client.dto.YoutubeVideoInfo;
import com.kculture.content.exception.ContentNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * YouTube Data API v3로 곡 메타데이터(제목/채널/썸네일/공개여부)를 가져온다.
 * 영상 자체는 절대 내려받지 않는다 — 공식 메타데이터만 조회한다.
 */
@Component
public class YoutubeDataClient {

    private final RestClient client;
    private final String apiKey;

    public YoutubeDataClient(
            RestClient youtubeRestClient,
            @Value("${youtube.api-key:}") String apiKey
    ) {
        this.client = youtubeRestClient;
        this.apiKey = apiKey;
    }

    public YoutubeVideoInfo fetch(String videoId) {
        JsonNode resp = client.get()
                .uri(uri -> uri.path("/videos")
                        .queryParam("part", "snippet,status,contentDetails")
                        .queryParam("id", videoId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode item = (resp == null) ? null : resp.path("items").path(0);
        if (item == null || item.isMissingNode() || item.isEmpty()) {
            throw new ContentNotFoundException("유튜브 영상을 찾을 수 없습니다: " + videoId);
        }

        JsonNode snippet = item.path("snippet");
        JsonNode status = item.path("status");

        String title = snippet.path("title").asText(null);
        String channelTitle = snippet.path("channelTitle").asText(null);
        if (title == null || title.isBlank() || channelTitle == null || channelTitle.isBlank()) {
            throw new IllegalArgumentException("유튜브 메타데이터에 제목/채널명이 없습니다: " + videoId);
        }

        return new YoutubeVideoInfo(
                title,
                channelTitle,
                snippet.path("thumbnails").path("high").path("url").asText(null),
                status.path("privacyStatus").asText(null),
                status.path("embeddable").asBoolean(false)
        );
    }
}
