package com.careerq.service;

import com.careerq.followup.FollowupDtos.CreateRequest;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.security.AuthUser;

import java.util.List;

public interface FollowupService {

    FollowupView create(AuthUser sender, Long questionId, CreateRequest request);

    List<FollowupView> list(AuthUser current, Long questionId);
}
