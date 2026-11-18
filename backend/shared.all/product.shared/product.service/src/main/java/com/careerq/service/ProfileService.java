package com.careerq.service;


import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.profile.ProfileDtos.UpdateRequest;
import com.careerq.profile.ProfileDtos.PatchRequest;
import com.careerq.security.AuthUser;

public interface ProfileService {

    ProfileView get(AuthUser user);

    ProfileView update(AuthUser user, UpdateRequest request);

    ProfileView patch(AuthUser user, PatchRequest request);

}
