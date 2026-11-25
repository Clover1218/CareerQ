package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.hr.HrPostDtos.*;
import com.careerq.security.AuthUser;

public interface HrPostService {
    PageResult<PublicShareSummary> listPublicShares(Long cursor, int limit);
    PageResult<PublicShareSummary> listPublicSharesByAuthor(Long authorId, Long cursor, int limit);
    PublicShareDetail getPublicShare(Long id);
    PageResult<HrPostSummary> list(AuthUser hr, String status, Long cursor, int limit);
    HrPostView get(AuthUser hr, Long id);
    HrPostView createDraft(AuthUser hr, CreateDraftRequest request);
    HrPostView saveDraft(AuthUser hr, Long id, SaveDraftRequest request);
    HrPostView submit(AuthUser hr, Long id, long version);
    HrPostView withdraw(AuthUser hr, Long id, long version);
    void deleteDraft(AuthUser hr, Long id, long version);
}
