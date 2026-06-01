package com.qamanager.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 이메일 발송 서비스(로그인 OTP 용).
 *
 * - SMTP 미설정 환경(로컬/테스트)에서는 JavaMailSender 빈이 없을 수 있어 ObjectProvider 로 optional 주입.
 * - @Async 로 비동기 발송 → 로그인 응답을 막지 않는다. (가상 스레드 executor 사용)
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final SecurityOtpProperties props;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider, SecurityOtpProperties props) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.props = props;
    }

    public boolean isAvailable() {
        return mailSender != null;
    }

    @Async
    public void sendLoginOtp(String to, String code) {
        if (mailSender == null) {
            log.warn("SMTP 미설정 — OTP 메일 미발송 (수신자={}). spring.mail.host 설정 필요.", EmailMasker.mask(to));
            return;
        }
        long ttlMinutes = Math.max(1, props.getOtp().getTtlSeconds() / 60);
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.getMailFrom());
        msg.setTo(to);
        msg.setSubject("[QA Manager] 로그인 인증 코드");
        msg.setText(
            "QA Manager 로그인 인증 코드입니다.\n\n"
            + "인증 코드: " + code + "\n\n"
            + "이 코드는 " + ttlMinutes + "분간 유효합니다.\n"
            + "본인이 요청하지 않았다면 이 메일을 무시하세요."
        );
        try {
            mailSender.send(msg);
            log.info("로그인 OTP 메일 발송 완료 (수신자={})", EmailMasker.mask(to));
        } catch (Exception e) {
            // 발송 실패해도 사용자에겐 일반 메시지. 재발송(resend) 경로로 복구 가능.
            log.error("로그인 OTP 메일 발송 실패 (수신자={}): {}", EmailMasker.mask(to), e.getMessage());
        }
    }
}
