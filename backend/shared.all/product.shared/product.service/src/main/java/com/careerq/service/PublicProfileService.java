package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.hr.HrPostDtos.PublicShareSummary;
import com.careerq.profile.PublicProfileDtos.*;

public interface PublicProfileService {
    Profile profile(Long id);
    PageResult<QuestionEntry> questions(Long id, Long cursor, int limit);
    PageResult<AnswerEntry> answers(Long id, Long cursor, int limit);
    PageResult<PublicShareSummary> shares(Long id, Long cursor, int limit);
}
