package com.careerq.service;

import com.careerq.roundtable.RoundtableDtos.Featured;
import com.careerq.roundtable.RoundtableDtos.Roundtable;
import com.careerq.roundtable.RoundtableDtos.TopicChoice;
import com.careerq.security.AuthUser;

public interface RoundtableService {

    Roundtable current(AuthUser user);

    Featured featured();

    Roundtable nominate(AuthUser student, TopicChoice choice);

    Roundtable vote(AuthUser student, TopicChoice choice);
}
