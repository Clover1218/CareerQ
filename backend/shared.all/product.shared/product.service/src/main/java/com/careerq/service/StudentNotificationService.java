package com.careerq.service;

import com.careerq.student.StudentNotificationDtos.*;
import com.careerq.security.AuthUser;

public interface StudentNotificationService {
    StudentNotificationPage list(AuthUser hr, String category, boolean unreadOnly, Long cursor, int limit);
    StudentUnreadCounts unread(AuthUser hr);
    StudentNotification read(AuthUser hr, Long id);
    void readAll(AuthUser hr, long throughId);

    /** 业务事务内写入；不存问题/回答正文或内部审核备注。 */
    void answerPublished(Long answerId);
    void questionLiked(Long questionId, Long actorId);
    void contentReviewed(String targetType, Long targetId, String decision);
}
