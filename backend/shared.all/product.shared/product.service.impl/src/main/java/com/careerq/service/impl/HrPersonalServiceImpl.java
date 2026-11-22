package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.dao.HrPersonalDao;
import com.careerq.dao.HrProfileDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.HrAnswerRecordDO;
import com.careerq.dataobject.HrProfileDO;
import com.careerq.dataobject.UserDO;
import com.careerq.hr.HrPersonalDtos.*;
import com.careerq.security.AuthUser;
import com.careerq.service.HrPersonalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class HrPersonalServiceImpl implements HrPersonalService {
    private final UserDao users;
    private final HrProfileDao profiles;
    private final HrPersonalDao personal;
    private final ObjectMapper json;

    public HrPersonalServiceImpl(UserDao users, HrProfileDao profiles, HrPersonalDao personal, ObjectMapper json) {
        this.users = users;
        this.profiles = profiles;
        this.personal = personal;
        this.json = json;
    }

    private UserDO requireHr(AuthUser hr) {
        if (!"hr".equals(hr.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR 可访问个人中心");
        UserDO user = users.findById(hr.getId()).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!"hr".equals(user.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return user;
    }

    private HrProfileDO currentProfile(Long id) {
        return profiles.findByUserId(id).orElseGet(() -> {
            HrProfileDO profile = new HrProfileDO();
            profile.setUserId(id);
            profile.setCompanyDisplay("某行业企业");
            return profile;
        });
    }

    @Override
    public HrProfileView profile(AuthUser hr) {
        return toView(requireHr(hr), currentProfile(hr.getId()));
    }

    @Override
    @Transactional
    public HrProfileView patchProfile(AuthUser hr, HrProfilePatch patch) {
        requireHr(hr);
        personal.lockUser(hr.getId());
        HrProfileDO profile = currentProfile(hr.getId());
        if (patch.nickname() != null) {
            if (patch.nickname().isBlank()) throw new BusinessException(ErrorCode.INVALID_PARAM, "昵称不能为空");
            personal.updateNickname(hr.getId(), patch.nickname().trim());
        }
        if (patch.companyDisplay() != null) {
            profile.setCompanyDisplay(patch.companyDisplay().isBlank() ? "某行业企业" : patch.companyDisplay().trim());
        }
        if (patch.industry() != null) profile.setIndustry(patch.industry().trim());
        if (patch.position() != null) profile.setPosition(patch.position().trim());
        if (patch.bio() != null) profile.setBio(patch.bio().trim());
        if (patch.specialties() != null) {
            if (patch.specialties().stream().anyMatch(Objects::isNull)) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "擅长领域不能为空值");
            }
            List<String> tags = patch.specialties().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
            try {
                profile.setSpecialtiesJson(json.writeValueAsString(tags));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法保存擅长领域", e);
            }
        }
        if (profiles.findByUserId(hr.getId()).isEmpty()) profiles.insert(profile);
        profiles.updatePersonal(profile);
        return toView(users.findById(hr.getId()).orElseThrow(), profile);
    }

    private HrProfileView toView(UserDO user, HrProfileDO profile) {
        List<String> tags = List.of();
        if (profile.getSpecialtiesJson() != null && !profile.getSpecialtiesJson().isBlank()) {
            try {
                tags = json.readValue(profile.getSpecialtiesJson(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法读取擅长领域", e);
            }
        }
        return new HrProfileView(user.getId(), user.getNickname(), user.getAvatarUrl(), profile.getCompanyDisplay(),
                profile.getIndustry(), profile.getPosition(), profile.getBio(), tags, profile.getVerifyStatus());
    }

    @Override
    public HrContributionStats stats(AuthUser hr) {
        requireHr(hr);
        return personal.stats(hr.getId());
    }

    @Override
    public PageResult<HrAnswerRecord> answers(AuthUser hr, String kind, String status, Long cursor, int limit) {
        requireHr(hr);
        if (!Set.of("all", "root", "reply").contains(kind)
                || !Set.of("all", "published", "reviewing", "rejected", "withdrawn", "downlisted", "removed").contains(status)
                || cursor != null && cursor <= 0 || limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "回答记录筛选或翻页参数无效");
        }
        List<HrAnswerRecordDO> rows = personal.answers(hr.getId(), kind, status, cursor, limit + 1);
        List<HrAnswerRecordDO> page = rows.stream().limit(limit).toList();
        Long next = rows.size() > limit ? page.get(page.size() - 1).getId() : null;
        return new PageResult<>(page.stream().map(this::answerView).toList(), next);
    }

    private HrAnswerRecord answerView(HrAnswerRecordDO answer) {
        boolean available = "published".equals(answer.getQuestionStatus());
        boolean removed = "removed".equals(answer.getStatus());
        return new HrAnswerRecord(answer.getId(), answer.getQuestionId(),
                available ? answer.getQuestionTitle() : "原问题当前不可查看", available,
                answer.getFollowupId() == null ? answer.getId() : answer.getRootAnswerId(), answer.getFollowupId(),
                removed ? "" : answer.getBody(), answer.getStatus(), answer.getRemovalSource(),
                removed ? 0 : answer.getLikeCount(), removed ? 0 : answer.getUsefulCount(), answer.getCreatedAt());
    }

    @Override
    public HrAnswerRecord answer(AuthUser hr, Long answerId) {
        requireHr(hr);
        return personal.answer(hr.getId(), answerId).map(this::answerView)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答记录不存在"));
    }
}
