-- HR 工作台按本人查询追问待办，并检查每条追问的有效回复。
CREATE INDEX idx_followups_hr_status_question ON followups (hr_id, status, question_id, created_at);
CREATE INDEX idx_answers_followup_author_status ON answers (followup_id, author_id, status, question_id);
