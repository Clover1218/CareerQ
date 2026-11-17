package com.careerq.tag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TagApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsCategoriesForAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/tags/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[?(@.name == '求职阶段')].tagCount", hasItem(7)))
                .andExpect(jsonPath("$.data[?(@.name == '求职准备')].tagCount", hasItem(7)));
    }

    @Test
    void filtersTagsByCategory() throws Exception {
        mockMvc.perform(get("/api/v1/tags").param("category", "求职准备"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[?(@.category != '求职准备')]").isEmpty())
                .andExpect(jsonPath("$.data[0].name").value("简历"));
    }

    @Test
    void searchesTagsGloballyIgnoringCategory() throws Exception {
        mockMvc.perform(get("/api/v1/tags")
                        .param("category", "求职阶段")
                        .param("keyword", "简历"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("简历"))
                .andExpect(jsonPath("$.data[0].category").value("求职准备"));
    }

    @Test
    void matchesLowerCaseSlug() throws Exception {
        mockMvc.perform(get("/api/v1/tags").param("keyword", "OFFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Offer选择"));
    }

    @Test
    void returnsRecommendedTagsWithoutCategory() throws Exception {
        mockMvc.perform(get("/api/v1/tags").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.category == null)]").isEmpty());
    }

    @Test
    void clampsLimitToCatalogSize() throws Exception {
        mockMvc.perform(get("/api/v1/tags").param("limit", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(33));
    }

    @Test
    void returnsEmptyForUnknownCategory() throws Exception {
        mockMvc.perform(get("/api/v1/tags").param("category", "不存在的分类"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
