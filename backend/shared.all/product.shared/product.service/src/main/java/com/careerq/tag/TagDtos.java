package com.careerq.tag;

public final class TagDtos {

    private TagDtos() {
    }

    public record TagView(Long id, String name, String slug, String category) {
    }

    public record TagCategoryView(String name, int tagCount) {
    }
}
