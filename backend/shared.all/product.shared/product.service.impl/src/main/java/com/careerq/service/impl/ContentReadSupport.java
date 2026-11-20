package com.careerq.service.impl;

import com.careerq.common.UserBrief;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.UserDao;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Request-local display data; no shared cache that could retain stale names or viewer state. */
@Component
class ContentReadSupport {

    private static final int BATCH_SIZE = 500;

    private final UserDao userDao;
    private final InteractionDao interactionDao;

    ContentReadSupport(UserDao userDao, InteractionDao interactionDao) {
        this.userDao = userDao;
        this.interactionDao = interactionDao;
    }

    Map<Long, UserBrief> authors(List<Long> authorIds) {
        List<Long> ids = distinctIds(authorIds);
        Map<Long, UserBrief> authors = new HashMap<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            for (UserBrief author : userDao.findBriefsByIds(ids.subList(start, Math.min(start + BATCH_SIZE, ids.size())))) {
                authors.put(author.id(), author);
            }
        }
        return authors;
    }

    UserBrief author(Map<Long, UserBrief> authors, Long id) {
        return authors.getOrDefault(id, new UserBrief(id, "用户"));
    }

    InteractionStates interactions(Long viewerId, String targetType, List<Long> targetIds) {
        if (viewerId == null) {
            return new InteractionStates(Map.of());
        }
        List<Long> ids = distinctIds(targetIds);
        Map<Long, Set<String>> states = new HashMap<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            var rows = interactionDao.findByUserAndTargets(viewerId, targetType,
                    ids.subList(start, Math.min(start + BATCH_SIZE, ids.size())));
            for (var row : rows) {
                states.computeIfAbsent(row.getTargetId(), ignored -> new HashSet<>()).add(row.getType());
            }
        }
        return new InteractionStates(states);
    }

    private List<Long> distinctIds(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    record InteractionStates(Map<Long, Set<String>> states) {
        boolean has(Long targetId, String type) {
            return states.getOrDefault(targetId, Set.of()).contains(type);
        }
    }
}
