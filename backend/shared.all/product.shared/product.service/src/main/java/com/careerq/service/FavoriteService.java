package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.favorite.FavoriteDtos.FavoriteView;
import com.careerq.security.AuthUser;

public interface FavoriteService {

    /** 我的收藏（按收藏时间倒序，游标分页）；targetType 可选 question/answer/post。 */
    PageResult<FavoriteView> list(AuthUser user, String targetType, Long cursor, int limit);
}