package com.careerq.service;


import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.profile.ProfileDtos.UpdateRequest;
import com.careerq.security.AuthUser;

public interface ProfileService {

    ProfileView get(AuthUser user);

    ProfileView update(AuthUser user, UpdateRequest request);

    /** 更新头像地址（空字符串表示清空）。 */
    ProfileView updateAvatar(AuthUser user, String avatarUrl);

}
