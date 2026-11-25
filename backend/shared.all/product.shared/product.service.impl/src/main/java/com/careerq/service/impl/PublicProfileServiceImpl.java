package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.dao.HrProfileDao;
import com.careerq.dao.PublicProfileDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.HrProfileDO;
import com.careerq.dataobject.UserDO;
import com.careerq.hr.HrPostDtos.PublicShareSummary;
import com.careerq.profile.PublicProfileDtos.*;
import com.careerq.service.HrPostService;
import com.careerq.service.PublicProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicProfileServiceImpl implements PublicProfileService {
    private final UserDao users;
    private final HrProfileDao hrProfiles;
    private final PublicProfileDao contents;
    private final HrPostService posts;
    private final ObjectMapper json;

    public PublicProfileServiceImpl(UserDao users, HrProfileDao hrProfiles, PublicProfileDao contents,
                                    HrPostService posts, ObjectMapper json) {
        this.users = users;
        this.hrProfiles = hrProfiles;
        this.contents = contents;
        this.posts = posts;
        this.json = json;
    }

    private UserDO publicUser(Long id) {
        return users.findById(id)
                .filter(user -> "active".equals(user.getStatus()))
                .filter(user -> "student".equals(user.getRole()) || "hr".equals(user.getRole()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公开主页暂不可查看"));
    }

    private void validatePage(Long cursor, int limit) {
        if ((cursor != null && cursor <= 0) || limit < 1 || limit > 50)
            throw new BusinessException(ErrorCode.INVALID_PARAM, "分页参数无效");
    }

    @Override
    public Profile profile(Long id) {
        UserDO user = publicUser(id);
        if ("student".equals(user.getRole())) {
            boolean showEducation = user.isEducationPublic();
            return new Profile(id, user.getRole(), user.getNickname(), user.getAvatarUrl(),
                    showEducation ? user.getSchool() : null, showEducation ? user.getMajor() : null,
                    showEducation ? user.getGrade() : null, null, null, null, user.getPublicBio(),
                    List.of(), null, contents.studentStats(id));
        }
        HrProfileDO hr = hrProfiles.findByUserId(id).orElseGet(HrProfileDO::new);
        List<String> specialties = List.of();
        if (hr.getSpecialtiesJson() != null && !hr.getSpecialtiesJson().isBlank()) {
            try {
                specialties = json.readValue(hr.getSpecialtiesJson(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法读取擅长领域", e);
            }
        }
        return new Profile(id, user.getRole(), user.getNickname(), user.getAvatarUrl(), null, null, null,
                hr.getCompanyDisplay(), hr.getIndustry(), hr.getPosition(), hr.getBio(), specialties,
                hr.getVerifyStatus(), contents.hrStats(id));
    }

    @Override
    public PageResult<QuestionEntry> questions(Long id, Long cursor, int limit) {
        UserDO user = publicUser(id);
        validatePage(cursor, limit);
        if (!"student".equals(user.getRole())) return PageResult.of(List.of());
        var rows = contents.questions(id, cursor, limit + 1);
        var page = rows.stream().limit(limit).map(q -> new QuestionEntry(q.getId(), q.getTitle(),
                excerpt(q.getBody()), q.getCreatedAt(), q.getAnswerCount(), q.getFollowupCount())).toList();
        return new PageResult<>(page, rows.size() > limit ? page.getLast().id() : null);
    }

    @Override
    public PageResult<AnswerEntry> answers(Long id, Long cursor, int limit) {
        UserDO user = publicUser(id);
        validatePage(cursor, limit);
        if (!"hr".equals(user.getRole())) return PageResult.of(List.of());
        var rows = contents.answers(id, cursor, limit + 1);
        var page = rows.stream().limit(limit).map(a -> new AnswerEntry(a.getId(), a.getQuestionId(),
                a.getFollowupId(), a.getQuestionTitle(), excerpt(a.getBody()), a.getCreatedAt(),
                a.getLikeCount(), a.getUsefulCount())).toList();
        return new PageResult<>(page, rows.size() > limit ? page.getLast().id() : null);
    }

    @Override
    public PageResult<PublicShareSummary> shares(Long id, Long cursor, int limit) {
        UserDO user = publicUser(id);
        validatePage(cursor, limit);
        if (!"hr".equals(user.getRole())) return PageResult.of(List.of());
        return posts.listPublicSharesByAuthor(id, cursor, limit);
    }

    private String excerpt(String body) {
        if (body == null) return "";
        String text = body.strip();
        int length = text.codePointCount(0, text.length());
        return length <= 240 ? text : text.substring(0, text.offsetByCodePoints(0, 240)) + "…";
    }
}
