package com.careerq.common;

import java.util.List;

public record PageResult<T>(List<T> items, Long nextCursor) {

    public static <T> PageResult<T> of(List<T> items) {
        return new PageResult<>(items, null);
    }
}
