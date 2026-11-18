package com.careerq.feed;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 推荐流快照游标：首次请求固定快照时间、候选上界与探索种子，后续页面在
 * 同一快照上重算完整排序再切片，避免抖动/多样性重排造成跨页重复或遗漏。
 */
public record FeedCursor(long snapshotEpochSecond, long maxId, long seed, int page) {

    private static final String VERSION = "v1";

    public static FeedCursor first(long maxId) {
        return new FeedCursor(Instant.now().getEpochSecond(), maxId,
                ThreadLocalRandom.current().nextLong(), 0);
    }

    public FeedCursor next() {
        return new FeedCursor(snapshotEpochSecond, maxId, seed, page + 1);
    }

    public LocalDateTime snapshotTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(snapshotEpochSecond), ZoneId.systemDefault());
    }

    public String encode() {
        String raw = VERSION + ":" + snapshotEpochSecond + ":" + maxId + ":" + seed + ":" + page;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 5 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("版本不匹配");
            }
            long snapshot = Long.parseLong(parts[1]);
            long maxId = Long.parseLong(parts[2]);
            long seed = Long.parseLong(parts[3]);
            int page = Integer.parseInt(parts[4]);
            if (snapshot <= 0 || maxId < 0 || page < 0) {
                throw new IllegalArgumentException("字段越界");
            }
            return new FeedCursor(snapshot, maxId, seed, page);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "游标不合法");
        }
    }
}
