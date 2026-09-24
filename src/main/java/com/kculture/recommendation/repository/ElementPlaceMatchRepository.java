package com.kculture.recommendation.repository;

import com.kculture.recommendation.domain.ElementPlaceMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ElementPlaceMatchRepository extends JpaRepository<ElementPlaceMatch, Long> {

    // 요소별 추천 장소 (노출 순서 → 점수순)
    List<ElementPlaceMatch> findByElement_IdOrderByDisplayOrderAscMatchScoreDesc(Long elementId);

    // 세션 구성용: 여러 요소의 매칭 후보 한 번에 (점수 높은 순)
    List<ElementPlaceMatch> findByElement_IdInOrderByMatchScoreDesc(List<Long> elementIds);

    // 세션 서빙 시 매칭 근거(reason) 조회 — (element_id, place_id) 조합이 여러 행이어도
    // 예외 없이 가장 점수 높은 하나만 안전하게 선택한다.
    Optional<ElementPlaceMatch> findFirstByElement_IdAndPlace_IdOrderByMatchScoreDesc(Long elementId, Long placeId);
}
