package com.kculture.content.service;

import com.kculture.content.client.YoutubeDataClient;
import com.kculture.content.client.dto.YoutubeVideoInfo;
import com.kculture.content.domain.Song;
import com.kculture.content.dto.SongResponse;
import com.kculture.content.exception.ContentNotFoundException;
import com.kculture.content.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SongService {

    private final SongRepository songRepository;
    private final YoutubeDataClient youtubeDataClient;

    public List<SongResponse> findAllSongs() {
        return songRepository.findAll().stream()
                // song엔터티에서 곡을 찾아서 객체로 분류
                // song -> <<< song을 오른쪽으로 바꿔라
                // 새로운 객체에 들어갈 값
                // 받은 값을 다시 리스트로 모아서 반환
                .map(song -> new SongResponse(
                        song.getId(),
                        song.getTitle(),
                        song.getArtist(),
                        song.getThumbnailUrl()
                ))
                .toList();
    }

    public SongResponse findSong(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("곡을 찾을 수 없습니다."));

        return new SongResponse(
                song.getId(),
                song.getTitle(),
                song.getArtist(),
                song.getThumbnailUrl()
        );
    }

    public List<SongResponse> searchSongs(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력하세요.");
        }
        keyword = keyword.strip();
        return songRepository.findByTitleContainingIgnoreCase(keyword)
                .stream()
                .map(song -> new SongResponse(
                        song.getId(),
                        song.getTitle(),
                        song.getArtist(),
                        song.getThumbnailUrl()
                ))
                .toList();
    }

    // 공개 MV의 video id로 곡을 등록한다. 메타데이터(제목/가수/썸네일)는 YouTube Data API로 자동 수집한다.
    @Transactional
    public SongResponse createFromYoutube(String youtubeVideoId) {
        String videoId = youtubeVideoId.strip();
        return songRepository.findByYoutubeVideoId(videoId)
                .map(this::toResponse)                       // 이미 있으면 재사용
                .orElseGet(() -> {
                    YoutubeVideoInfo info = youtubeDataClient.fetch(videoId);
                    try {
                        Song saved = songRepository.save(new Song(
                                info.title(),
                                info.channelTitle(),             // 채널명을 아티스트로 사용
                                videoId,
                                info.thumbnailUrl()
                        ));
                        return toResponse(saved);
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청이 같은 videoId를 먼저 등록한 경우 — 그 곡을 그대로 재사용한다.
                        return songRepository.findByYoutubeVideoId(videoId)
                                .map(this::toResponse)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(
                song.getId(), song.getTitle(), song.getArtist(), song.getThumbnailUrl()
        );
    }
}
