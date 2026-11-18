package com.careerq.tag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TagCatalogTests {

    private static final int EXPECTED_SIZE = 33;
    private static final Set<String> EXPECTED_CATEGORIES = Set.of(
            "求职阶段", "求职准备", "方向选择", "Offer 与入职", "职场成长", "求职心态");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record TagRow(String name, String slug, String category, int weight) {
    }

    @Test
    void seedsFixedTagCatalog() {
        List<TagRow> rows = jdbcTemplate.query(
                "select name, slug, category, weight from tags where category is not null",
                (rs, index) -> new TagRow(
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("category"),
                        rs.getInt("weight")));

        assertEquals(EXPECTED_SIZE, rows.size());
        assertEquals(EXPECTED_CATEGORIES,
                rows.stream().map(TagRow::category).collect(Collectors.toSet()));
        assertTrue(rows.stream().allMatch(row -> row.weight() > 0), "固定标签应带排序权重");

        Set<String> slugs = rows.stream().map(TagRow::slug).collect(Collectors.toSet());
        assertTrue(slugs.containsAll(Set.of(
                "实习", "简历", "面试准备", "秋招", "职业规划",
                "跨专业求职", "offer选择", "应届生身份")));
        assertTrue(rows.stream().anyMatch(row -> "跨专业求职".equals(row.name())),
                "长标签名不应被截断");
        assertTrue(rows.stream().anyMatch(row -> "Offer选择".equals(row.name())
                        && "offer选择".equals(row.slug())),
                "大小写混排标签的 slug 应统一为小写");
    }
}
