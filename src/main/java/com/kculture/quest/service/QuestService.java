package com.kculture.quest.service;

import com.kculture.quest.domain.*;
import com.kculture.quest.dto.*;
import com.kculture.quest.exception.QuestNotFoundException;
import com.kculture.quest.repository.*;
import com.kculture.travel.domain.Place;
import com.kculture.travel.repository.PlaceRepository;
import com.kculture.recommendation.domain.*;
import com.kculture.recommendation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestService {

    private final QuestRepository questRepository;
    private final QuestStepRepository stepRepository;
    private final MissionRepository missionRepository;
    private final PlaceRepository placeRepository;
    private final RecommendationSessionRepository sessionRepository;
    private final SessionPlaceRepository sessionPlaceRepository;

    public List<QuestResponse> findAllQuests() {
        return questRepository.findByActiveTrueOrderByIdDesc().stream()
                .map(QuestResponse::from)
                .toList();
    }

    public QuestDetailResponse findQuest(Long questId) {
        Quest quest = findQuestEntity(questId);
        List<QuestStepResponse> steps = stepRepository.findByQuestIdOrderByStepOrderAsc(questId).stream()
                .map(step -> QuestStepResponse.from(
                        step,
                        missionRepository.findByStepId(step.getId())
                                .map(MissionResponse::from)
                                .orElse(null)
                ))
                .toList();
        return new QuestDetailResponse(QuestResponse.from(quest), steps);
    }

    // 퀘스트, 단계, 미션을 하나의 트랜잭션에서 함께 저장한다.
    @Transactional
    public QuestResponse createQuest(QuestCreateRequest request) {
        Quest quest = questRepository.save(new Quest(
                request.title(), request.themeRegion(), request.themeEra(),
                QuestOriginType.CURATED, null, null, request.description()
        ));

        int order = 1;
        for (QuestCreateRequest.StepRequest stepRequest : request.steps()) {
            Place place = placeRepository.findById(stepRequest.placeId())
                    .orElseThrow(() -> new QuestNotFoundException(
                            "장소를 찾을 수 없습니다: " + stepRequest.placeId()
                    ));
            if (place.getLatitude() == null || place.getLongitude() == null) {
                throw new IllegalArgumentException("GPS 좌표가 없는 장소는 퀘스트에 넣을 수 없습니다.");
            }
            if (stepRequest.mission().missionType() == MissionType.QUIZ
                    && (stepRequest.mission().answer() == null || stepRequest.mission().answer().isBlank())) {
                throw new IllegalArgumentException("퀴즈 미션에는 정답이 필요합니다.");
            }

            QuestStep step = stepRepository.save(new QuestStep(
                    quest, place, order++, stepRequest.story(),
                    stepRequest.distanceHint(), stepRequest.arrivalRadius()
            ));

            missionRepository.save(new Mission(
                    step, stepRequest.mission().missionType(),
                    stepRequest.mission().question(), stepRequest.mission().answer()
            ));
        }
        return QuestResponse.from(quest);
    }


    // 추천 세션에서 선택한 장소들을 순서대로 퀘스트 단계와 기본 사진 미션으로 만든다.
    @Transactional
    public QuestResponse createQuestFromSession(QuestFromSessionRequest request) {
        return questRepository.findBySessionId(request.sessionId())
                .map(QuestResponse::from)
                .orElseGet(() -> {
                    try {
                        return createNewQuestFromSession(request);
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청이 같은 세션으로 먼저 퀘스트를 만든 경우 — 그 퀘스트를 그대로 반환한다.
                        return questRepository.findBySessionId(request.sessionId())
                                .map(QuestResponse::from)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private QuestResponse createNewQuestFromSession(QuestFromSessionRequest request) {
        RecommendationSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new QuestNotFoundException("추천 세션을 찾을 수 없습니다."));

        if (session.getUser() == null || !session.getUser().getId().equals(request.userId())) {
            throw new IllegalArgumentException("본인의 추천 세션만 퀘스트로 만들 수 있습니다.");
        }
        if (session.getStatus() != RecommendationStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 추천 세션만 퀘스트로 만들 수 있습니다.");
        }

        List<SessionPlace> selectedPlaces = sessionPlaceRepository
                .findBySessionIdAndChosenTrueOrderByDisplayOrderAsc(session.getId());
        if (selectedPlaces.isEmpty()) {
            throw new IllegalArgumentException("선택된 추천 장소가 없습니다.");
        }

        Quest quest = questRepository.save(new Quest(
                request.title(), null, null, QuestOriginType.FROM_SONG,
                session.getSong(), session, request.description()
        ));

        int order = 1;
        for (SessionPlace selected : selectedPlaces) {
            Place place = selected.getPlace();
            if (place.getLatitude() == null || place.getLongitude() == null) {
                throw new IllegalArgumentException("GPS 좌표가 없는 장소는 퀘스트에 넣을 수 없습니다.");
            }
            QuestStep step = stepRepository.save(new QuestStep(
                    quest, place, order++,
                    place.getName() + "에 도착했습니다. 주변 문화를 살펴보세요.",
                    null, 50
            ));
            missionRepository.save(new Mission(
                    step, MissionType.PHOTO, "이 장소에서 찍은 사진을 등록하세요.", null
            ));
        }

        session.changeStatus(RecommendationStatus.CONVERTED);
        return QuestResponse.from(quest);
    }

    public Quest findQuestEntity(Long questId) {
        return questRepository.findById(questId)
                .filter(Quest::isActive)
                .orElseThrow(() -> new QuestNotFoundException("퀘스트를 찾을 수 없습니다."));
    }
}
