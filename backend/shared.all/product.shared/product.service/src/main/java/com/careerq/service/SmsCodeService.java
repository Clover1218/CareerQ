package com.careerq.service;

public interface SmsCodeService {

    void send(String phone);

    boolean verify(String phone, String code);
}
