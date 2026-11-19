package com.careerq.feed;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/** 最新排序的 seek 游标：以 (createdAt, id) 作为下一段起点，插入新问题不会导致跨页重复。 */
public record FeedSeekCursor(String sort, long filterHash,
                             LocalDateTime lastCreatedAt, long lastId) {

    private static final String VERSION = "v2";

    public String encode() {
        java.time.Instant instant = lastCreatedAt.atZone(ZoneId.systemDefault()).toInstant();
        String raw = VERSION + ":" + sort + ":" + filterHash + ":"
                + instant.getEpochSecond() + ":" + instant.getNano() + ":" + lastId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedSeekCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 6 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("版本不匹配");
            }
            long epochSecond = Long.parseLong(parts[3]);
            int nano = Integer.parseInt(parts[4]);
            long lastId = Long.parseLong(parts[5]);
            if (epochSecond <= 0 || nano < 0 || nano > 999_999_999 || lastId <= 0) {
                throw new IllegalArgumentException("字段越界");
            }
            return new FeedSeekCursor(parts[1], Long.parseLong(parts[2]),
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nano), ZoneId.systemDefault()),
                    lastId);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "游标不合法");
        }
    }
}
