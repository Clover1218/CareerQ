package com.careerq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信验证码服务。
 * dev 模式下使用固定验证码（不发送真实短信），prod 模式生成随机码并预留供应商接入点。
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final Duration TTL = Duration.ofMinutes(5);

    private final boolean devMode;
    private final String devCode;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public SmsCodeService(
            @Value("${careerq.sms.dev-mode:true}") boolean devMode,
            @Value("${careerq.sms.dev-code:123456}") String devCode) {
        this.devMode = devMode;
        this.devCode = devCode;
    }

    public void send(String phone) {
        if (devMode) {
            log.info("[dev] 短信验证码 for {} = {}（固定码，不发送真实短信）", phone, devCode);
            return;
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        codes.put(phone, new CodeEntry(code, Instant.now().plus(TTL)));
        // TODO(M2)：接入真实短信供应商后在此发送
        log.info("已生成短信验证码 for {}（供应商未接入，仅记录）", phone);
    }

    public boolean verify(String phone, String code) {
        if (devMode) {
            return devCode.equals(code);
        }
        CodeEntry entry = codes.get(phone);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return false;
        }
        if (!entry.code().equals(code)) {
            return false;
        }
        codes.remove(phone);
        return true;
    }

    private record CodeEntry(String code, Instant expiresAt) {
    }
}
