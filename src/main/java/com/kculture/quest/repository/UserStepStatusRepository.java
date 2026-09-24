package com.kculture.quest.repository;

import com.kculture.quest.domain.UserStepStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserStepStatusRepository extends JpaRepository<UserStepStatus, Long> {
    Optional<UserStepStatus> findByUserIdAndQuestStepId(Long userId, Long stepId);

    // questStep과 그 place까지 한 번에 즉시 로딩해서 진행 응답 조립 시 N+1을 막는다.
    @EntityGraph(attributePaths = {"questStep", "questStep.place"})
    List<UserStepStatus> findByUserIdAndQuestStepQuestIdOrderByQuestStepStepOrderAsc(Long userId, Long questId);
}
