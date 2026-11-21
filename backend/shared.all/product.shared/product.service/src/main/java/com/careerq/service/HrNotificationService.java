package com.careerq.service;

import com.careerq.hr.HrNotificationDtos.*;
import com.careerq.security.AuthUser;

public interface HrNotificationService {
    HrNotificationPage list(AuthUser hr, String category, boolean unreadOnly, Long cursor, int limit);
    HrUnreadCounts unread(AuthUser hr);
    HrNotification read(AuthUser hr, Long id);
    void readAll(AuthUser hr, long throughId);

    /** 业务事务内写入；不存问题/回答正文或内部审核备注。 */
    void followupPublished(Long followupId);
    void answerReviewed(Long answerId, String decision);
    void answerRemoved(Long answerId);
}
