package com.kculture.content.repository;

import com.kculture.content.domain.Song;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SongRepository extends JpaRepository<Song, Long> {

    List<Song> findByTitleContainingIgnoreCase(String title);

    // 동일 영상 중복 등록 방지 / 재사용
    Optional<Song> findByYoutubeVideoId(String youtubeVideoId);

    // Song 행을 잠근 채로 조회 — 동시 분석 생성 요청이 이 곡에 대해 직렬화되도록 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Song s where s.id = :id")
    Optional<Song> findByIdForUpdate(Long id);
}
