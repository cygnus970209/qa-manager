package com.qamanager.notification;

import com.qamanager.member.TeamMemberRepository;
import com.qamanager.notification.teams.TeamsNotifier;
import com.qamanager.project.ProjectRepository;
import com.qamanager.qa.item.QaItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link NotificationService#distinctRecipients} 단위 테스트.
 * - actor 본인은 제외
 * - null 은 제외
 * - 중복은 한 번만
 * - LinkedHashSet 기반이므로 입력 순서가 유지된다
 */
class NotificationServiceDistinctRecipientsTest {

    private NotificationService service;

    @BeforeEach
    void setUp() {
        // distinctRecipients 는 의존성을 쓰지 않는 순수 로직이므로 mock 으로 통과.
        service = new NotificationService(
            mock(NotificationRepository.class),
            mock(TeamMemberRepository.class),
            mock(ProjectRepository.class),
            mock(QaItemRepository.class),
            mock(SseEmitterRegistry.class),
            mock(TeamsNotifier.class)
        );
    }

    @Nested
    @DisplayName("actor 본인은 항상 수신자에서 제외된다")
    class ExcludesActor {
        @Test
        @DisplayName("후보가 actor 한 명뿐이면 빈 집합")
        void onlyActor_returnsEmpty() {
            Set<Long> result = service.distinctRecipients(1L, 1L);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("후보 중 actor 가 섞여 있으면 actor 만 빠진다")
        void mixedWithActor_excludesActor() {
            Set<Long> result = service.distinctRecipients(1L, 1L, 2L, 3L);
            assertThat(result).containsExactly(2L, 3L);
        }
    }

    @Nested
    @DisplayName("null 은 항상 제외된다")
    class ExcludesNull {
        @Test
        @DisplayName("모든 후보가 null 이면 빈 집합")
        void allNull_returnsEmpty() {
            Set<Long> result = service.distinctRecipients(1L, null, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("일부만 null 이면 나머지는 포함")
        void someNull_keepsRest() {
            Set<Long> result = service.distinctRecipients(99L, null, 2L, null, 3L);
            assertThat(result).containsExactly(2L, 3L);
        }
    }

    @Nested
    @DisplayName("중복은 한 번만 포함된다 (예: 같은 사람이 tester 이자 assignee1)")
    class Deduplicates {
        @Test
        @DisplayName("같은 ID 가 여러 슬롯에 있어도 한 번만")
        void sameMemberAcrossSlots_appearsOnce() {
            // 시나리오: tester=2, assignee1=2, assignee2=3 (actor=1)
            Set<Long> result = service.distinctRecipients(1L, 2L, 2L, 3L);
            assertThat(result).containsExactly(2L, 3L);
        }

        @Test
        @DisplayName("3슬롯 모두 같은 사람일 때 한 명만 반환")
        void allSamePerson_returnsOne() {
            Set<Long> result = service.distinctRecipients(1L, 5L, 5L, 5L);
            assertThat(result).containsExactly(5L);
        }
    }

    @Nested
    @DisplayName("입력 순서는 유지된다")
    class PreservesOrder {
        @Test
        @DisplayName("LinkedHashSet 이므로 tester → assignee1 → assignee2 순")
        void preservesInsertionOrder() {
            Set<Long> result = service.distinctRecipients(99L, 7L, 3L, 5L);
            assertThat(result).containsExactly(7L, 3L, 5L);
        }
    }

    @Test
    @DisplayName("actor=null 이면 모든 non-null 후보가 포함된다 (시스템 발행 케이스)")
    void nullActor_includesAllNonNull() {
        Set<Long> result = service.distinctRecipients(null, 1L, 2L, 3L);
        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("후보가 비어있으면 빈 집합")
    void noCandidates_returnsEmpty() {
        Set<Long> result = service.distinctRecipients(1L);
        assertThat(result).isEmpty();
    }
}
