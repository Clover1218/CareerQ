package com.careerq.service;

import com.careerq.me.MeDtos.Stats;
import com.careerq.security.AuthUser;

public interface MeService {

    Stats stats(AuthUser user);
}
