package com.careerq.service;

import java.time.LocalDateTime;
import java.util.Map;

/** 用户标签偏好：interactions 是事实源，偏好表是可重建的物化缓存。 */
public interface TagAffinityService {

    /** 按当前仍存在的互动重算该用户的全部标签偏好。 */
    void recompute(Long userId);

    /** 返回 now 时刻的有效偏好（已按 computed_at 继续衰减）。 */
    Map<Long, Double> effectiveScores(Long userId, LocalDateTime now);
}
