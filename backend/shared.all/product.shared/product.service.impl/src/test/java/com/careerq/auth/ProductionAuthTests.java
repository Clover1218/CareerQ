package com.careerq.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:careerq-prod-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "careerq.sms.dev-mode=true"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionAuthTests {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"sms-code", "login", "register"})
    void smsAuthIsUnavailableWithoutProvider(String operation) throws Exception {
        String payload = "sms-code".equals(operation)
                ? """
                  {"phone":"13800000001"}
                  """
                : """
                  {"phone":"13800000001","code":"123456"}
                  """;
        mockMvc.perform(post("/api/v1/auth/{operation}", operation).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }
}
