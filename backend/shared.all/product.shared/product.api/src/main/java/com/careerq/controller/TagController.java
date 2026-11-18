package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.service.TagService;
import com.careerq.tag.TagDtos.TagCategoryView;
import com.careerq.tag.TagDtos.TagView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<TagCategoryView>> categories() {
        return ApiResponse.ok(tagService.categories());
    }

    @GetMapping
    public ApiResponse<List<TagView>> list(@RequestParam(required = false) String category,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(tagService.list(category, keyword, limit));
    }
}
