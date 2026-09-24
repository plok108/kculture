package com.kculture.quest.controller;

import com.kculture.common.auth.CurrentUserId;
import com.kculture.quest.dto.*;
import com.kculture.quest.service.QuestProgressService;
import com.kculture.quest.service.QuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestService questService;
    private final QuestProgressService progressService;

    @GetMapping
    public List<QuestResponse> findAllQuests() {
        return questService.findAllQuests();
    }

    @GetMapping("/{questId}")
    public QuestDetailResponse findQuest(@PathVariable Long questId) {
        return questService.findQuest(questId);
    }

    @PostMapping
    public QuestResponse createQuest(@Valid @RequestBody QuestCreateRequest request) {
        return questService.createQuest(request);
    }

    @PostMapping("/from-session")
    public QuestResponse createQuestFromSession(
            @Valid @RequestBody QuestFromSessionRequest request
    ) {
        return questService.createQuestFromSession(request);
    }

    @PostMapping("/{questId}/start")
    public QuestProgressResponse startQuest(
            @PathVariable Long questId,
            @CurrentUserId Long userId
    ) {
        return progressService.startQuest(userId, questId);
    }

    @GetMapping("/{questId}/progress")
    public QuestProgressResponse findProgress(
            @PathVariable Long questId,
            @CurrentUserId Long userId
    ) {
        return progressService.findProgress(userId, questId);
    }

    @PostMapping("/{questId}/steps/{stepId}/arrival")
    public ArrivalResponse checkArrival(
            @PathVariable Long questId,
            @PathVariable Long stepId,
            @CurrentUserId Long userId,
            @Valid @RequestBody LocationRequest request
    ) {
        return progressService.checkArrival(userId, questId, stepId, request);
    }

    @PostMapping("/{questId}/steps/{stepId}/complete")
    public QuestProgressResponse completeMission(
            @PathVariable Long questId,
            @PathVariable Long stepId,
            @CurrentUserId Long userId,
            @Valid @RequestBody MissionCompleteRequest request
    ) {
        return progressService.completeMission(userId, questId, stepId, request);
    }
}
