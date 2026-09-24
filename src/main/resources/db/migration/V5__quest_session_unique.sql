-- 추천 세션 → 퀘스트 변환 시 동시 요청으로 같은 세션에서 퀘스트가 중복 생성되는 것을 DB 레벨에서 막는다.
-- session_id는 CURATED(수동 생성) 퀘스트에서는 NULL이며, MySQL은 UNIQUE 제약에서 NULL을 여러 번 허용하므로 영향 없다.
ALTER TABLE quests
    ADD UNIQUE KEY uk_quests_session (session_id);
