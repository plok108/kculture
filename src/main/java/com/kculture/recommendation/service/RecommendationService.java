package com.kculture.recommendation.service;

import com.kculture.content.domain.AnalysisStatus;
import com.kculture.content.domain.CulturalElement;
import com.kculture.content.domain.MvAnalysis;
import com.kculture.content.domain.Song;
import com.kculture.content.repository.CulturalElementRepository;
import com.kculture.content.repository.MvAnalysisRepository;
import com.kculture.content.repository.SongRepository;
import com.kculture.recommendation.domain.ElementPlaceMatch;
import com.kculture.recommendation.domain.RecommendationSession;
import com.kculture.recommendation.domain.RecommendationStatus;
import com.kculture.recommendation.domain.SessionPlace;
import com.kculture.recommendation.dto.MatchResponse;
import com.kculture.recommendation.dto.SessionPlaceResponse;
import com.kculture.recommendation.dto.SessionResponse;
import com.kculture.recommendation.repository.ElementPlaceMatchRepository;
import com.kculture.recommendation.repository.RecommendationSessionRepository;
import com.kculture.recommendation.repository.SessionPlaceRepository;
import com.kculture.travel.domain.Place;
import com.kculture.user.domain.User;
import com.kculture.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final MvAnalysisRepository mvAnalysisRepository;
    private final CulturalElementRepository culturalElementRepository;
    private final ElementPlaceMatchRepository elementPlaceMatchRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final SessionPlaceRepository sessionPlaceRepository;

    // 곡 검색 → 추천 세션 생성 (요소별 장소 1개, 지역 분산 선별)
    @Transactional
    public SessionResponse createSession(Long songId, Long userId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "곡을 찾을 수 없습니다."));

        User user = (userId != null)
                ? userRepository.findById(userId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."))
                : null;

        // 곡의 최신 DONE 분석 → 요소들
        MvAnalysis analysis = mvAnalysisRepository
                .findFirstBySong_IdAndStatusOrderByFinishedAtDesc(songId, AnalysisStatus.DONE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "분석이 완료된 데이터가 없습니다."));

        List<CulturalElement> elements = culturalElementRepository
                .findByAnalysisIdOrderByTimestampSecAscIdAsc(analysis.getId());
        if (elements.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "추출된 한국적 요소가 없습니다.");
        }

        // 요소별 매칭 후보 (유효점수 = matchScore + regionBonus 내림차순)
        List<Long> elementIds = elements.stream().map(CulturalElement::getId).toList();
        Map<Long, List<ElementPlaceMatch>> candidatesByElement = elementPlaceMatchRepository
                .findByElement_IdInOrderByMatchScoreDesc(elementIds).stream()
                .collect(Collectors.groupingBy(m -> m.getElement().getId()));

        RecommendationSession session = sessionRepository.save(new RecommendationSession(user, song));

        // 지역 분산 그리디 선별
        Set<String> usedRegions = new HashSet<>();
        Set<Long> usedPlaceIds = new HashSet<>();
        int order = 1;

        for (CulturalElement element : elements) {
            List<ElementPlaceMatch> candidates = candidatesByElement.getOrDefault(element.getId(), List.of())
                    .stream()
                    .filter(m -> !usedPlaceIds.contains(m.getPlace().getId()))
                    .sorted(Comparator.comparing(this::effectiveScore).reversed())
                    .toList();
            if (candidates.isEmpty()) {
                continue; // 이 요소는 추천 가능한 장소 없음 → skip
            }

            // 아직 안 쓴 지역 우선, 없으면 최고점 후보
            ElementPlaceMatch picked = candidates.stream()
                    .filter(m -> {
                        String region = m.getPlace().getRegionSido();
                        return region != null && !usedRegions.contains(region);
                    })
                    .findFirst()
                    .orElse(candidates.get(0));

            Place place = picked.getPlace();
            sessionPlaceRepository.save(new SessionPlace(session, place, element, order++));
            usedPlaceIds.add(place.getId());
            if (place.getRegionSido() != null) {
                usedRegions.add(place.getRegionSido());
            }
        }

        return buildSessionResponse(session);
    }

    // 세션 + 순서대로 추천 장소들
    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId) {
        RecommendationSession session = findSessionOrThrow(sessionId);
        return buildSessionResponse(session);
    }

    // S04 노출 시점 기록
    @Transactional
    public SessionPlaceResponse markShown(Long sessionPlaceId, Long userId) {
        SessionPlace sp = findSessionPlaceOrThrow(sessionPlaceId);
        checkOwnership(sp, userId);
        sp.markShownNow();
        return toResponse(sp);
    }

    // 코스에 담기/빼기
    @Transactional
    public SessionPlaceResponse choosePlace(Long sessionPlaceId, boolean chosen, Long userId) {
        SessionPlace sp = findSessionPlaceOrThrow(sessionPlaceId);
        checkOwnership(sp, userId);
        sp.markChosen(chosen);
        return toResponse(sp);
    }

    // 세션에 소유자(회원)가 있는 경우에만 요청자와 일치하는지 검증한다.
    // 비회원(게스트) 세션은 소유자가 없으므로 검사를 건너뛴다.
    private void checkOwnership(SessionPlace sp, Long userId) {
        User owner = sp.getSession().getUser();
        if (owner != null && !owner.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 추천 세션만 수정할 수 있습니다.");
        }
    }

    // 세션 상태 전환 (CONVERTED / EXPIRED 등)
    @Transactional
    public SessionResponse updateStatus(Long sessionId, String statusValue) {
        RecommendationSession session = findSessionOrThrow(sessionId);
        session.changeStatus(parseStatus(statusValue));
        return buildSessionResponse(session);
    }

    // 요소별 추천 장소 목록
    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesForElement(Long elementId) {
        return elementPlaceMatchRepository
                .findByElement_IdOrderByDisplayOrderAscMatchScoreDesc(elementId).stream()
                .map(MatchResponse::from)
                .toList();
    }

    // ===== 내부 헬퍼 =====

    private SessionResponse buildSessionResponse(RecommendationSession session) {
        List<SessionPlaceResponse> places = sessionPlaceRepository
                .findBySession_IdOrderByDisplayOrderAsc(session.getId()).stream()
                .map(this::toResponse)
                .toList();
        return SessionResponse.of(session, places);
    }

    // SessionPlace → 응답 (매칭 근거 reason 조회 포함)
    private SessionPlaceResponse toResponse(SessionPlace sp) {
        String reason = null;
        if (sp.getElement() != null) {
            reason = elementPlaceMatchRepository
                    .findFirstByElement_IdAndPlace_IdOrderByMatchScoreDesc(sp.getElement().getId(), sp.getPlace().getId())
                    .map(ElementPlaceMatch::getReason)
                    .orElse(null);
        }
        return SessionPlaceResponse.from(sp, reason);
    }

    // 유효점수 = matchScore + regionBonus (null은 0 취급)
    private BigDecimal effectiveScore(ElementPlaceMatch m) {
        BigDecimal score = m.getMatchScore() != null ? m.getMatchScore() : BigDecimal.ZERO;
        BigDecimal bonus = m.getRegionBonus() != null ? m.getRegionBonus() : BigDecimal.ZERO;
        return score.add(bonus);
    }

    private RecommendationSession findSessionOrThrow(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "추천 세션을 찾을 수 없습니다."));
    }

    private SessionPlace findSessionPlaceOrThrow(Long sessionPlaceId) {
        return sessionPlaceRepository.findById(sessionPlaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "추천 장소를 찾을 수 없습니다."));
    }

    private RecommendationStatus parseStatus(String value) {
        try {
            return RecommendationStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 상태값입니다: " + value);
        }
    }
}
