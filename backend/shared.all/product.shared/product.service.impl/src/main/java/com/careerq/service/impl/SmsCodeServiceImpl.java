package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.service.SmsCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 开发环境使用固定验证码。真实短信接入前，其他环境返回服务不可用。
 */
@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    private final boolean devMode;
    private final String devCode;
    public SmsCodeServiceImpl(
            Environment environment,
            @Value("${careerq.sms.dev-mode:false}") boolean devMode,
            @Value("${careerq.sms.dev-code:123456}") String devCode) {
        this.devMode = devMode && environment.matchesProfiles("dev & !prod");
        this.devCode = devCode;
    }

    @Override
    public void send(String phone) {
        requireAvailable();
    }

    @Override
    public boolean verify(String phone, String code) {
        requireAvailable();
        return devCode.equals(code);
    }

    private void requireAvailable() {
        if (!devMode) {
            throw new BusinessException(ErrorCode.SMS_UNAVAILABLE);
        }
    }
}
