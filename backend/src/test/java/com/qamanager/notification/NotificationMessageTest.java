package com.qamanager.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 본문 조립 단위 테스트 — {@link NotificationService#excerpt} / {@link NotificationService#withExcerpt}.
 * 코멘트류 알림은 "<문구>: <댓글 발췌>" 로, 발췌는 공백 정리 + 길이 제한.
 */
class NotificationMessageTest {

    @Test
    @DisplayName("줄바꿈·연속 공백은 한 칸으로 정리하고 앞뒤 공백은 제거한다")
    void excerpt_normalizesWhitespace() {
        assertThat(NotificationService.excerpt("  첫 줄\n\n둘째   줄\t끝  ", 200))
            .isEqualTo("첫 줄 둘째 줄 끝");
    }

    @Test
    @DisplayName("max 를 넘으면 잘라서 … 을 붙이고, 전체 길이는 max 이하다")
    void excerpt_truncatesWithEllipsis() {
        String longText = "가".repeat(300);
        String ex = NotificationService.excerpt(longText, 200);
        assertThat(ex).hasSize(200).endsWith("…");
    }

    @Test
    @DisplayName("자르는 위치가 이모지(서로게이트 쌍) 중간이면 한 글자 앞에서 자른다")
    void excerpt_doesNotSplitSurrogatePair() {
        String text = "가".repeat(198) + "😀" + "나".repeat(50); // 😀 는 char 2개 → index 198,199
        String ex = NotificationService.excerpt(text, 200);
        assertThat(ex).isEqualTo("가".repeat(198) + "…");
    }

    @Test
    @DisplayName("max 이하면 그대로, null 은 빈 문자열")
    void excerpt_shortOrNull() {
        assertThat(NotificationService.excerpt("짧은 댓글", 200)).isEqualTo("짧은 댓글");
        assertThat(NotificationService.excerpt(null, 200)).isEmpty();
    }

    @Test
    @DisplayName("본문은 '문구: 발췌' 형태, 발췌가 비면 문구만")
    void withExcerpt_format() {
        assertThat(NotificationService.withExcerpt("새 코멘트가 달렸습니다", "확인 부탁드립니다."))
            .isEqualTo("새 코멘트가 달렸습니다: 확인 부탁드립니다.");
        assertThat(NotificationService.withExcerpt("새 코멘트가 달렸습니다", "   "))
            .isEqualTo("새 코멘트가 달렸습니다");
    }

    @Test
    @DisplayName("문구 + 최대 발췌를 합쳐도 message 컬럼(500) 안에 들어간다")
    void withExcerpt_fitsMessageColumn() {
        String msg = NotificationService.withExcerpt("내 코멘트에 답글이 달렸습니다", "가".repeat(1000));
        assertThat(msg.length()).isLessThanOrEqualTo(NotificationService.MESSAGE_MAX);
    }
}
