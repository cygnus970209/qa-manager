package com.qamanager.auth.otp;

import com.qamanager.common.ApiException;
import com.qamanager.member.TeamMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 로그인 이메일 OTP 발급/검증. 저장은 Redis(TTL 자동 만료).
 *
 * Redis 키:
 *  - auth:otp:&lt;challengeId&gt;  (Hash) memberId, codeHash(BCrypt), email, attempts, lastSentAt  — TTL=otp.ttl
 *  - auth:otp:member:&lt;memberId&gt; = challengeId  — 사용자당 활성 1개 보장
 */
@Service
public class LoginOtpService {

    private static final Logger log = LoggerFactory.getLogger(LoginOtpService.class);
    private static final String OTP_PREFIX = "auth:otp:";
    private static final String MEMBER_PREFIX = "auth:otp:member:";

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecurityOtpProperties props;
    private final SecureRandom random = new SecureRandom();

    public LoginOtpService(StringRedisTemplate redis,
                           PasswordEncoder passwordEncoder,
                           MailService mailService,
                           SecurityOtpProperties props) {
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.props = props;
    }

    /** OTP 발급 결과(코드 자체는 메일로만 전달, 응답엔 포함하지 않음). */
    public record Challenge(String challengeId, String maskedEmail, long expiresInSeconds) {}

    /** 검증 결과. 성공 시 memberId, 실패 시 error 코드 + 남은 시도. */
    public record VerifyResult(boolean success, Long memberId, String error, int remainingAttempts) {
        static VerifyResult ok(Long memberId) { return new VerifyResult(true, memberId, null, 0); }
        static VerifyResult fail(String error, int remaining) { return new VerifyResult(false, null, error, remaining); }
    }

    /** 새 OTP 발급 + 메일 발송. 동일 사용자의 이전 challenge 는 폐기. */
    public Challenge issue(TeamMember member, String email) {
        String memberKey = MEMBER_PREFIX + member.getId();
        String prev = redis.opsForValue().get(memberKey);
        if (prev != null) redis.delete(OTP_PREFIX + prev);

        String code = generateCode();
        String challengeId = UUID.randomUUID().toString();
        String key = OTP_PREFIX + challengeId;
        Duration ttl = Duration.ofSeconds(props.getOtp().getTtlSeconds());

        Map<String, String> data = Map.of(
            "memberId", String.valueOf(member.getId()),
            "codeHash", passwordEncoder.encode(code),
            "email", email,
            "attempts", "0",
            "lastSentAt", String.valueOf(Instant.now().getEpochSecond())
        );
        redis.opsForHash().putAll(key, data);
        redis.expire(key, ttl);
        redis.opsForValue().set(memberKey, challengeId, ttl);

        mailService.sendLoginOtp(email, code);
        log.info("로그인 OTP 발급 memberId={} challengeId={}", member.getId(), challengeId);
        return new Challenge(challengeId, EmailMasker.mask(email), props.getOtp().getTtlSeconds());
    }

    /** OTP 검증. 성공 시 challenge 폐기. */
    public VerifyResult verify(String challengeId, String code) {
        String key = OTP_PREFIX + challengeId;
        Map<Object, Object> data = redis.opsForHash().entries(key);
        if (data.isEmpty()) return VerifyResult.fail("expired", 0);

        int maxAttempts = props.getOtp().getMaxAttempts();
        long attempts = redis.opsForHash().increment(key, "attempts", 1);
        if (attempts > maxAttempts) {
            invalidate(challengeId, data);
            return VerifyResult.fail("too_many_attempts", 0);
        }

        String codeHash = (String) data.get("codeHash");
        if (codeHash != null && passwordEncoder.matches(code, codeHash)) {
            Long memberId = Long.valueOf((String) data.get("memberId"));
            invalidate(challengeId, data);
            return VerifyResult.ok(memberId);
        }
        int remaining = (int) Math.max(0, maxAttempts - attempts);
        return VerifyResult.fail("invalid_code", remaining);
    }

    /** OTP 재발송(쿨다운 적용). 새 코드로 교체하고 시도 횟수 리셋. */
    public Challenge resend(String challengeId) {
        String key = OTP_PREFIX + challengeId;
        Map<Object, Object> data = redis.opsForHash().entries(key);
        if (data.isEmpty()) {
            throw ApiException.unauthorized("인증 세션이 만료되었습니다. 다시 로그인해 주세요.");
        }

        long lastSent = Long.parseLong((String) data.getOrDefault("lastSentAt", "0"));
        long cooldown = props.getOtp().getResendCooldownSeconds();
        long elapsed = Instant.now().getEpochSecond() - lastSent;
        if (elapsed < cooldown) {
            throw ApiException.tooManyRequests("잠시 후 다시 시도해 주세요. (" + (cooldown - elapsed) + "초 남음)");
        }

        String email = (String) data.get("email");
        String code = generateCode();
        Duration ttl = Duration.ofSeconds(props.getOtp().getTtlSeconds());
        redis.opsForHash().put(key, "codeHash", passwordEncoder.encode(code));
        redis.opsForHash().put(key, "attempts", "0");
        redis.opsForHash().put(key, "lastSentAt", String.valueOf(Instant.now().getEpochSecond()));
        redis.expire(key, ttl);

        mailService.sendLoginOtp(email, code);
        log.info("로그인 OTP 재발송 challengeId={}", challengeId);
        return new Challenge(challengeId, EmailMasker.mask(email), props.getOtp().getTtlSeconds());
    }

    private void invalidate(String challengeId, Map<Object, Object> data) {
        redis.delete(OTP_PREFIX + challengeId);
        Object memberId = data.get("memberId");
        if (memberId != null) redis.delete(MEMBER_PREFIX + memberId);
    }

    private String generateCode() {
        int len = props.getOtp().getLength();
        int bound = (int) Math.pow(10, len);
        return String.format("%0" + len + "d", random.nextInt(bound));
    }
}
