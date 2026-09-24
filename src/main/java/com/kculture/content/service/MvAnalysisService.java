package com.kculture.content.service;

import com.kculture.content.domain.*;
import com.kculture.content.dto.*;
import com.kculture.content.exception.ContentNotFoundException;
import com.kculture.content.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MvAnalysisService {

    private final SongRepository songRepository;
    private final MvAnalysisRepository analysisRepository;
    private final CulturalElementRepository elementRepository;

    // 곡에 대한 분석 작업을 생성한다. 실패하지 않은 최신 작업이 있으면 재사용한다.
    @Transactional
    public AnalysisResponse createAnalysis(Long songId, String modelName) {
        // Song 행을 잠근 채로 "재사용 가능한 분석 있는지 확인 → 없으면 생성"을 수행해서
        // 동시 요청이 같은 곡에 대해 중복 분석을 만들지 못하도록 직렬화한다.
        Song song = songRepository.findByIdForUpdate(songId)
                .orElseThrow(() -> new ContentNotFoundException("곡을 찾을 수 없습니다."));

        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("모델 이름을 입력하세요.");
        }

        return analysisRepository.findFirstBySongIdOrderByIdDesc(songId)
                .filter(analysis -> analysis.getStatus() != AnalysisStatus.FAILED)
                .map(AnalysisResponse::from)
                .orElseGet(() -> AnalysisResponse.from(
                        analysisRepository.save(new MvAnalysis(song, modelName.strip()))
                ));
    }

    public List<AnalysisResponse> findAnalyses(Long songId) {
        if (!songRepository.existsById(songId)) {
            throw new ContentNotFoundException("곡을 찾을 수 없습니다.");
        }
        return analysisRepository.findBySongIdOrderByIdDesc(songId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    public AnalysisResponse findAnalysis(Long analysisId) {
        return AnalysisResponse.from(findEntity(analysisId));
    }

    public List<CulturalElementResponse> findElements(Long analysisId) {
        findEntity(analysisId);
        return elementRepository.findByAnalysisIdOrderByTimestampSecAscIdAsc(analysisId).stream()
                .map(CulturalElementResponse::from)
                .toList();
    }

    @Transactional
    public AnalysisResponse startAnalysis(Long analysisId) {
        MvAnalysis analysis = findEntity(analysisId);
        if (analysis.getStatus() != AnalysisStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 분석만 시작할 수 있습니다.");
        }
        analysis.start();
        return AnalysisResponse.from(analysis);
    }

    @Transactional
    public AnalysisResponse completeAnalysis(Long analysisId, AnalysisCompleteRequest request) {
        MvAnalysis analysis = findEntity(analysisId);
        if (analysis.getStatus() != AnalysisStatus.RUNNING) {
            throw new IllegalArgumentException("진행 중인 분석만 완료할 수 있습니다.");
        }

        List<CulturalElement> elements = request.elements().stream()
                .map(element -> new CulturalElement(
                        analysis, element.category(), element.name(), element.description(),
                        element.timestampSec(), element.confidence()
                ))
                .toList();

        elementRepository.saveAll(elements);
        analysis.finish(true);
        return AnalysisResponse.from(analysis);
    }

    @Transactional
    public AnalysisResponse failAnalysis(Long analysisId, String reason) {
        MvAnalysis analysis = findEntity(analysisId);
        // 비동기 러너 재호출 등으로 이미 종료된 경우엔 그대로 반환(멱등)
        if (analysis.getStatus() == AnalysisStatus.DONE || analysis.getStatus() == AnalysisStatus.FAILED) {
            return AnalysisResponse.from(analysis);
        }
        analysis.fail(reason);
        return AnalysisResponse.from(analysis);
    }

    // 곡 분석을 생성/재사용하고 PENDING이면 RUNNING으로 시작한다.
    // started=true인 경우에만 호출 측(컨트롤러)이 비동기 러너를 트리거한다.
    @Transactional
    public RunResult createAndStart(Long songId, String modelName) {
        // Song 행을 잠근 채로 "재사용 가능한 분석 있는지 확인 → 없으면 생성"을 수행해서
        // 동시 요청이 같은 곡에 대해 중복 분석(+중복 Gemini 호출)을 만들지 못하도록 직렬화한다.
        Song song = songRepository.findByIdForUpdate(songId)
                .orElseThrow(() -> new ContentNotFoundException("곡을 찾을 수 없습니다."));
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("모델 이름을 입력하세요.");
        }
        MvAnalysis analysis = analysisRepository.findFirstBySongIdOrderByIdDesc(songId)
                .filter(a -> a.getStatus() != AnalysisStatus.FAILED)
                .orElseGet(() -> analysisRepository.save(new MvAnalysis(song, modelName.strip())));

        boolean started = false;
        if (analysis.getStatus() == AnalysisStatus.PENDING) {
            analysis.start();
            started = true;
        }
        return new RunResult(AnalysisResponse.from(analysis), started);
    }

    // 분석 응답 + 이번 호출에서 새로 시작했는지 여부
    public record RunResult(AnalysisResponse analysis, boolean started) {
    }

    private MvAnalysis findEntity(Long id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("분석 기록을 찾을 수 없습니다."));
    }
}
