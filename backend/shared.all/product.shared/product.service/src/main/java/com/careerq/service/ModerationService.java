package com.careerq.service;

public interface ModerationService {

    String decide(String text);

    void recordMachine(String targetType, Long targetId, String decision);

    void recordHuman(String targetType, Long targetId, String decision, Long operatorId, String note);
}
