package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.ExperienceDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.ExperienceDO;
import com.careerq.dataobject.UserDO;
import com.careerq.profile.ProfileDtos.ExperienceItem;
import com.careerq.profile.ProfileDtos.ExperienceView;
import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.profile.ProfileDtos.UpdateRequest;
import com.careerq.security.AuthUser;
import com.careerq.service.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserDao userDao;
    private final ExperienceDao experienceDao;

    public ProfileServiceImpl(UserDao userDao, ExperienceDao experienceDao) {
        this.userDao = userDao;
        this.experienceDao = experienceDao;
    }

    @Override
    public ProfileView get(AuthUser user) {
        UserDO userDO = userDao.findById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return toView(userDO);
    }

    @Override
    @Transactional
    public ProfileView update(AuthUser user, UpdateRequest request) {
        UserDO userDO = userDao.findById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        userDO.setSchool(blankToNull(request.school()));
        userDO.setMajor(blankToNull(request.major()));
        userDO.setGrade(blankToNull(request.grade()));
        userDO.setDegree(blankToNull(request.degree()));
        userDO.setGraduationYear(blankToNull(request.graduationYear()));
        userDao.updateProfile(userDO);

        experienceDao.deleteByUserId(user.getId());
        List<ExperienceItem> items = request.experiences() == null ? List.of() : request.experiences();
        for (ExperienceItem item : items) {
            ExperienceDO experience = new ExperienceDO();
            experience.setUserId(user.getId());
            experience.setType(normalizeType(item.type()));
            experience.setTitle(item.title().trim());
            experience.setRole(blankToNull(item.role()));
            experience.setStartDate(blankToNull(item.startDate()));
            experience.setEndDate(blankToNull(item.endDate()));
            experience.setDescription(blankToNull(item.description()));
            experience.setCreatedAt(LocalDateTime.now());
            experience.setUpdatedAt(LocalDateTime.now());
            experienceDao.insert(experience);
        }
        return toView(userDao.findById(user.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public ProfileView updateAvatar(AuthUser user, String avatarUrl) {
        userDao.findById(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        userDao.updateAvatar(user.getId(), blankToNull(avatarUrl));
        return toView(userDao.findById(user.getId()).orElseThrow());
    }

    private ProfileView toView(UserDO user) {
        List<ExperienceView> experiences = experienceDao.findByUserId(user.getId()).stream()
                .map(e -> new ExperienceView(e.getId(), e.getType(), e.getTitle(), e.getRole(),
                        e.getStartDate(), e.getEndDate(), e.getDescription()))
                .toList();
        return new ProfileView(user.getId(), user.getRole(), user.getNickname(), user.getAvatarUrl(),
                user.getSchool(), user.getMajor(), user.getGrade(), user.getDegree(),
                user.getGraduationYear(), experiences);
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase();
        if (!"internship".equals(value) && !"project".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "type 只能是 internship 或 project");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
