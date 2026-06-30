package com.example.demo.service.impl;

import com.example.demo.config.SmsConfig;
import com.example.demo.service.SmsService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SmsServiceImpl implements SmsService {
    private final SmsConfig smsConfig;
    private final RestTemplate restTemplate;
    private final Map<String, String> codeCache = new ConcurrentHashMap<>();
    private final Map<String, Long> codeExpireTime = new ConcurrentHashMap<>();

    public SmsServiceImpl(SmsConfig smsConfig) {
        this.smsConfig = smsConfig;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String sendVerificationCode(String phone) {
        // 生成6位验证码
        String code = generateCode();

        // 缓存验证码，有效期5分钟
        codeCache.put(phone, code);
        codeExpireTime.put(phone, System.currentTimeMillis() + 5 * 60 * 1000);

        // 如果启用了短信服务，发送真实短信
        if (smsConfig.isEnabled() && smsConfig.getApiKey() != null) {
            try {
                sendRealSms(phone, code);
                System.out.println("验证码已发送至 " + phone + "，验证码：" + code);
            } catch (Exception e) {
                System.err.println("发送短信失败：" + e.getMessage());
                // 即使发送失败，也返回验证码，以便测试
            }
        } else {
            // 模拟模式，直接打印验证码
            System.out.println("【模拟模式】验证码已发送至 " + phone + "，验证码：" + code);
        }

        return code;
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        // 检查验证码是否存在
        if (!codeCache.containsKey(phone)) {
            return false;
        }

        // 检查验证码是否过期
        Long expireTime = codeExpireTime.get(phone);
        if (expireTime == null || System.currentTimeMillis() > expireTime) {
            codeCache.remove(phone);
            codeExpireTime.remove(phone);
            return false;
        }

        // 检查验证码是否正确
        boolean result = code.equals(codeCache.get(phone));

        // 验证成功后移除验证码
        if (result) {
            codeCache.remove(phone);
            codeExpireTime.remove(phone);
        }

        return result;
    }

    private String generateCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    private void sendRealSms(String phone, String code) throws Exception {
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + smsConfig.getApiKey());
        headers.set("Content-Type", "application/json");

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("phone", phone);
        requestBody.put("templateId", smsConfig.getTemplateId());
        requestBody.put("params", new String[]{code, "5"}); // 验证码和有效期

        // 发送请求
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                smsConfig.getApiUrl(),
                HttpMethod.POST,
                entity,
                String.class
        );

        // 检查响应
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new Exception("发送短信失败：" + response.getBody());
        }
    }
}
