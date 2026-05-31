package org.example.yunpan.service;

import org.example.yunpan.config.VerificationStore;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final VerificationStore verificationStore;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, VerificationStore verificationStore,
                        @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.verificationStore = verificationStore;
        this.fromAddress = fromAddress;
    }

    public void sendCode(String toEmail) {
        if (!verificationStore.canResend(toEmail)) {
            throw new IllegalStateException("发送过于频繁，请60秒后再试");
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        verificationStore.store(toEmail, code);

        SimpleMailMessage msg = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) {
            msg.setFrom(fromAddress);
        }
        msg.setTo(toEmail);
        msg.setSubject("JiaLe Cloud 验证码");
        msg.setText("您的验证码是：" + code + "，5分钟内有效。请勿泄露。");

        mailSender.send(msg);
    }

    public boolean verifyCode(String email, String code) {
        return verificationStore.verify(email, code);
    }
}
