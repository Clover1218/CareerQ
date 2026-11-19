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
 * 快照页码游标（推荐/热门共用）：首次请求固定快照时间、候选上界与探索种子，
 * 后续页面在同一快照上重算完整排序再切片。sort 与筛选指纹随游标下发，
 * 切换排序或筛选条件后旧游标会被拒绝。
 */
public record FeedPageCursor(String sort, long filterHash, long snapshotEpochSecond,
                             long maxId, long seed, int page) {

    private static final String VERSION = "v2";

    public static FeedPageCursor first(String sort, long filterHash, long maxId) {
        return new FeedPageCursor(sort, filterHash, Instant.now().getEpochSecond(), maxId,
                ThreadLocalRandom.current().nextLong(), 0);
    }

    public FeedPageCursor next() {
        return new FeedPageCursor(sort, filterHash, snapshotEpochSecond, maxId, seed, page + 1);
    }

    public LocalDateTime snapshotTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(snapshotEpochSecond), ZoneId.systemDefault());
    }

    public String encode() {
        String raw = VERSION + ":" + sort + ":" + filterHash + ":" + snapshotEpochSecond
                + ":" + maxId + ":" + seed + ":" + page;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedPageCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 7 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("版本不匹配");
            }
            long snapshot = Long.parseLong(parts[3]);
            long maxId = Long.parseLong(parts[4]);
            long seed = Long.parseLong(parts[5]);
            int page = Integer.parseInt(parts[6]);
            if (snapshot <= 0 || maxId < 0 || page < 0) {
                throw new IllegalArgumentException("字段越界");
            }
            return new FeedPageCursor(parts[1], Long.parseLong(parts[2]), snapshot, maxId, seed, page);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "游标不合法");
        }
    }
}
