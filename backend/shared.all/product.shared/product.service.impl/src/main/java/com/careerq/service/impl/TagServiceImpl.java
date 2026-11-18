package com.careerq.service.impl;

import com.careerq.dao.TagDao;
import com.careerq.dataobject.TagDO;
import com.careerq.service.TagService;
import com.careerq.tag.TagDtos.TagCategoryView;
import com.careerq.tag.TagDtos.TagView;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TagServiceImpl implements TagService {

    private static final int MAX_LIMIT = 100;

    private final TagDao tagDao;

    public TagServiceImpl(TagDao tagDao) {
        this.tagDao = tagDao;
    }

    @Override
    public List<TagCategoryView> categories() {
        // 查询结果已按 weight 倒序，LinkedHashMap 保留首次出现的顺序即为大类热度顺序
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TagDO tag : tagDao.findCategorized()) {
            counts.merge(tag.getCategory(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new TagCategoryView(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<TagView> list(String category, String keyword, int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        List<TagDO> rows;
        if (keyword != null && !keyword.isBlank()) {
            rows = tagDao.search(keyword.trim(), size);
        } else if (category != null && !category.isBlank()) {
            rows = tagDao.findByCategory(category.trim(), size);
        } else {
            rows = tagDao.findRecommended(size);
        }
        return rows.stream().map(TagServiceImpl::toView).toList();
    }

    private static TagView toView(TagDO tag) {
        return new TagView(tag.getId(), tag.getName(), tag.getSlug(), tag.getCategory());
    }
}
