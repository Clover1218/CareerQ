package com.careerq.dataobject;

public class TagDO {

    private Long id;

    private String name;

    private String slug;

    private String category;

    private int weight;

    public static TagDO of(String name, String slug) {
        TagDO tag = new TagDO();
        tag.name = name;
        tag.slug = slug;
        return tag;
    }

    public Long getId() {
        return id;
    }
}
