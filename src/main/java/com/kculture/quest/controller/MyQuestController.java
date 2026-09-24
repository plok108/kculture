package com.kculture.quest.controller;

import com.kculture.common.auth.CurrentUserId;
import com.kculture.quest.dto.QuestProgressResponse;
import com.kculture.quest.dto.StampResponse;
import com.kculture.quest.service.QuestProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MyQuestController {

    private final QuestProgressService progressService;

    @GetMapping("/quests")
    public List<QuestProgressResponse> findUserQuests(@CurrentUserId Long userId) {
        return progressService.findUserQuests(userId);
    }

    @GetMapping("/stamps")
    public List<StampResponse> findUserStamps(@CurrentUserId Long userId) {
        return progressService.findUserStamps(userId);
    }
}
