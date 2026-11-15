package com.careerq.service;

import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 点赞 / 有用 / 收藏。
 * targetType：answer / question / post；type：like / useful / favorite。
 */
public interface InteractionService {

    /** 添加互动（幂等：重复点击不重复计数）。 */
    void interact(AuthUser user, Request request);

    /** 取消互动（幂等：未点过也返回值成功）。 */
    void uninteract(AuthUser user, Request request);

    record Request(@NotBlank String targetType, @NotNull Long targetId, @NotBlank String type) {
    }
}