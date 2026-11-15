package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.favorite.FavoriteDtos.FavoriteView;
import com.careerq.security.AuthUsers;
import com.careerq.service.FavoriteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<PageResult<FavoriteView>> list(Authentication authentication,
                                                      @RequestParam(required = false) String targetType,
                                                      @RequestParam(required = false) Long cursor,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(favoriteService.list(AuthUsers.require(authentication), targetType, cursor, limit));
    }
}