package com.careerq.roundtable;

import com.careerq.answer.AnswerDtos;
import com.careerq.dao.RoundtableDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.UserDO;
import com.careerq.question.QuestionDtos;
import com.careerq.roundtable.RoundtableDtos.TopicChoice;
import com.careerq.security.AuthUser;
import com.careerq.service.AnswerService;
import com.careerq.service.QuestionService;
import com.careerq.service.RoundtableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoundtableFlowTests {

    @Autowired RoundtableService roundtable;
    @Autowired RoundtableDao dao;
    @Autowired QuestionService questions;
    @Autowired AnswerService answers;
    @Autowired UserDao users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void studentsCanChangeTheirOnlyVoteAndThePublicBallotDoesNotExposeVoterIds() throws Exception {
        AuthUser student = createUser("student", "投票学生");
        AuthUser hr = createUser("hr", "圆桌答主");
        var week = roundtable.current(null).week();
        var first = new TopicChoice(week, question(student, "投票候选一"));
        var second = new TopicChoice(week, question(student, "投票候选二"));
        roundtable.nominate(student, first);
        roundtable.nominate(student, first);
        roundtable.nominate(student, second);

        mvc.perform(put("/api/v1/roundtable/vote").with(csrf()).with(user(hr))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(first)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/roundtable/vote").with(csrf()).with(user(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.myVote").value(first.questionId()));
        var changed = roundtable.vote(student, second);
        assertThat(changed.myVote()).isEqualTo(second.questionId());
        assertThat(changed.candidates()).extracting(RoundtableDtos.Candidate::votes).containsExactly(1L, 0L);

        mvc.perform(get("/api/v1/roundtable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myVote").doesNotExist())
                .andExpect(jsonPath("$.data.candidates[0].questionId").value(second.questionId()));
    }

    @Test
    void previousBallotWinnerShowsAtMostThreeSeatsAndCountsRepeatedAnswers() {
        AuthUser student = createUser("student", "提问学生");
        long topic = question(student, "上期票选话题");
        var week = roundtable.current(null).week().minusWeeks(1);
        dao.nominate(week, topic);
        dao.vote(week, student.getId(), topic);
        for (int i = 1; i <= 4; i++) {
            var hr = createUser("hr", "圆桌 HR " + i);
            answers.create(hr, topic, new AnswerDtos.CreateRequest("可以提前了解岗位的日常任务。", "detailed"));
            if (i == 1) {
                answers.create(hr, topic, new AnswerDtos.CreateRequest("也可以向面试官了解实习的培养方式。", "detailed"));
            }
        }

        var featured = roundtable.current(null).featured();
        assertThat(featured.questionId()).isEqualTo(topic);
        assertThat(featured.authorCount()).isEqualTo(4);
        assertThat(featured.answerCount()).isEqualTo(5);
        assertThat(featured.seats()).hasSize(3);
        assertThat(featured.seats()).extracting(seat -> seat.author().id()).doesNotHaveDuplicates();
    }

    private long question(AuthUser author, String title) {
        return questions.create(author, new QuestionDtos.CreateRequest(title, "关于实习选择，想听听有经验的建议。",
                List.of("实习"), "public")).id();
    }

    private AuthUser createUser(String role, String nickname) {
        UserDO user = new UserDO();
        user.setRole(role);
        user.setNickname(nickname);
        user.setStatus("active");
        users.insert(user);
        return AuthUser.of(user.getId(), role, nickname, "roundtable-test-" + user.getId());
    }
}
