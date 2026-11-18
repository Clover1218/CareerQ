package com.careerq.service.impl;

import com.careerq.dao.TagAffinityDao;
import com.careerq.dataobject.TagAffinityDO;
import com.careerq.dataobject.TagSignalDO;
import com.careerq.feed.FeedTuning;
import com.careerq.service.TagAffinityService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TagAffinityServiceImpl implements TagAffinityService {

    private final TagAffinityDao tagAffinityDao;

    public TagAffinityServiceImpl(TagAffinityDao tagAffinityDao) {
        this.tagAffinityDao = tagAffinityDao;
    }

    @Override
    @Transactional
    public void recompute(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> scores = new HashMap<>();
        for (TagSignalDO signal : tagAffinityDao.findQuestionSignals(userId)) {
            double weight = FeedTuning.weightOf(signal.getType());
            if (weight == 0) {
                continue;
            }
            double days = Math.max(0, Duration.between(signal.getCreatedAt(), now).toMinutes() / (60.0 * 24));
            scores.merge(signal.getTagId(),
                    weight * Math.pow(0.5, days / FeedTuning.AFFINITY_HALF_LIFE_DAYS),
                    Double::sum);
        }

        Set<Long> tagIds = new TreeSet<>(scores.keySet());
        for (TagAffinityDO row : tagAffinityDao.findByUser(userId)) {
            tagIds.add(row.getTagId());
        }

        for (Long tagId : tagIds) {
            double score = clamp(scores.getOrDefault(tagId, 0.0), FeedTuning.MAX_AFFINITY);
            TagAffinityDO existing = tagAffinityDao.findForUpdate(userId, tagId).orElse(null);
            if (existing == null) {
                TagAffinityDO row = new TagAffinityDO();
                row.setUserId(userId);
                row.setTagId(tagId);
                row.setScore(score);
                row.setComputedAt(now);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                try {
                    tagAffinityDao.insert(row);
                } catch (DuplicateKeyException conflict) {
                    TagAffinityDO raced = tagAffinityDao.findForUpdate(userId, tagId)
                            .orElseThrow(() -> conflict);
                    raced.setScore(score);
                    raced.setComputedAt(now);
                    raced.setUpdatedAt(now);
                    tagAffinityDao.update(raced);
                }
            } else {
                existing.setScore(score);
                existing.setComputedAt(now);
                existing.setUpdatedAt(now);
                tagAffinityDao.update(existing);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Double> effectiveScores(Long userId, LocalDateTime now) {
        Map<Long, Double> result = new HashMap<>();
        for (TagAffinityDO row : tagAffinityDao.findByUser(userId)) {
            double days = Math.max(0, Duration.between(row.getComputedAt(), now).toMinutes() / (60.0 * 24));
            double score = clamp(row.getScore() * Math.pow(0.5,
                    days / FeedTuning.AFFINITY_HALF_LIFE_DAYS), FeedTuning.MAX_AFFINITY);
            if (Math.abs(score) > 0.001) {
                result.put(row.getTagId(), score);
            }
        }
        return result;
    }

    private static double clamp(double value, double max) {
        return Math.max(-max, Math.min(max, value));
    }
}
