package com.careerq.service;

import com.careerq.tag.TagDtos.TagCategoryView;
import com.careerq.tag.TagDtos.TagView;

import java.util.List;

public interface TagService {

    List<TagCategoryView> categories();

    /**
     * 固定目录标签查询。
     * keyword 非空时忽略 category，全局搜索；category 为空时返回按权重排序的推荐标签。
     */
    List<TagView> list(String category, String keyword, int limit);
}
