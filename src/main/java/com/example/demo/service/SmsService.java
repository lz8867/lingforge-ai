package com.example.demo.service;

public interface SmsService {
    /**
     * 发送验证码短信
     * @param phone 手机号
     * @return 验证码
     */
    String sendVerificationCode(String phone);

    /**
     * 验证验证码
     * @param phone 手机号
     * @param code 验证码
     * @return 是否验证成功
     */
    boolean verifyCode(String phone, String code);
}
