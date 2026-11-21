package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.hr.HrPersonalDtos.*;
import com.careerq.security.AuthUser;

public interface HrPersonalService {
    HrProfileView profile(AuthUser hr);
    HrProfileView patchProfile(AuthUser hr, HrProfilePatch patch);
    HrContributionStats stats(AuthUser hr);
    PageResult<HrAnswerRecord> answers(AuthUser hr, String kind, String status, Long cursor, int limit);
    HrAnswerRecord answer(AuthUser hr, Long answerId);
}
