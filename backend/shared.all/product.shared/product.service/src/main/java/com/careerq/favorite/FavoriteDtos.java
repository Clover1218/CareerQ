package com.careerq.favorite;

import java.time.LocalDateTime;

public final class FavoriteDtos {

    private FavoriteDtos() {
    }

    public record FavoriteView(
            String targetType,
            Long targetId,
            String title,
            String excerpt,
            LocalDateTime createdAt) {
    }
}