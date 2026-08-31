package com.qamanager.qa.item;

import com.qamanager.integration.github.QaGithubIssueRepository;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import com.qamanager.project.Project;
import com.qamanager.projectupdate.ProjectUpdate;
import com.qamanager.projectupdate.ProjectUpdateRepository;
import com.qamanager.qa.shared.QaPriority;
import com.qamanager.qa.shared.QaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QaItemService 단위 테스트.
 * - create: testerId 가 null 이면 currentMemberId 가 자동으로 tester 로 지정된다
 * - update: tester/assignee1/assignee2 3슬롯 독립 처리 (변경 없음 / 명시적 clear / 신규 지정)
 * - update: 담당자 변경 시 이력 기록 + assignee 슬롯만 알림 이벤트 (tester 변경은 이벤트 없음)
 */
@ExtendWith(MockitoExtension.class)
class QaItemServiceTest {

    @Mock QaItemRepository qaRepository;
    @Mock QaHistoryRepository historyRepository;
    @Mock ProjectUpdateRepository updateRepository;
    @Mock TeamMemberRepository memberRepository;
    @Mock QaGithubIssueRepository githubIssueRepository;
    @Mock ApplicationEventPublisher events;

    @InjectMocks QaItemService service;

    private ProjectUpdate update;

    @BeforeEach
    void setUp() {
        // ProjectUpdate.getProject().getId() 체인 셋업
        update = mock(ProjectUpdate.class);
        Project project = mock(Project.class);
        lenient().when(update.getProject()).thenReturn(project);
        lenient().when(project.getId()).thenReturn(10L);
    }

    private TeamMember memberMock(Long id, String name) {
        TeamMember m = mock(TeamMember.class);
        lenient().when(m.getId()).thenReturn(id);
        lenient().when(m.getName()).thenReturn(name);
        return m;
    }

    @Nested
    @DisplayName("create — tester 자동 지정")
    class CreateTesterDefault {

        @Test
        @DisplayName("testerId 가 null 이면 currentMemberId 가 tester 로 지정된다")
        void nullTester_usesCurrentMember() {
            Long currentUserId = 99L;
            TeamMember currentUser = memberMock(currentUserId, "현재유저");
            when(updateRepository.findById(1L)).thenReturn(Optional.of(update));
            when(memberRepository.findByIdAndDeletedAtIsNull(currentUserId))
                .thenReturn(Optional.of(currentUser));
            when(qaRepository.save(any(QaItem.class))).thenAnswer(inv -> inv.getArgument(0));

            QaDto.CreateRequest req = new QaDto.CreateRequest(
                1L, "title", "desc", "cat",
                QaStatus.NEEDS_FIX,
                null, null, null, // testerId / assignee1Id / assignee2Id 모두 null
                QaPriority.MEDIUM,
                List.of(),
                null,             // createGithubIssue
                null, null        // githubRepoOwner / githubRepoName
            );

            service.create(req, currentUserId);

            ArgumentCaptor<QaItem> cap = ArgumentCaptor.forClass(QaItem.class);
            verify(qaRepository).save(cap.capture());
            assertThat(cap.getValue().getTester()).isSameAs(currentUser);
            assertThat(cap.getValue().getAssignee1()).isNull();
            assertThat(cap.getValue().getAssignee2()).isNull();
        }

        @Test
        @DisplayName("testerId 가 명시되면 그 멤버가 tester 로 지정된다")
        void explicitTester_usesGiven() {
            Long currentUserId = 99L;
            Long explicitTesterId = 7L;
            TeamMember explicitTester = memberMock(explicitTesterId, "지정테스터");
            when(updateRepository.findById(1L)).thenReturn(Optional.of(update));
            when(memberRepository.findByIdAndDeletedAtIsNull(explicitTesterId))
                .thenReturn(Optional.of(explicitTester));
            when(qaRepository.save(any(QaItem.class))).thenAnswer(inv -> inv.getArgument(0));

            QaDto.CreateRequest req = new QaDto.CreateRequest(
                1L, "title", null, null, QaStatus.NEEDS_FIX,
                explicitTesterId, null, null,
                QaPriority.MEDIUM, List.of(), null, null, null
            );

            service.create(req, currentUserId);

            ArgumentCaptor<QaItem> cap = ArgumentCaptor.forClass(QaItem.class);
            verify(qaRepository).save(cap.capture());
            assertThat(cap.getValue().getTester()).isSameAs(explicitTester);
        }

        @Test
        @DisplayName("assignee1 / assignee2 가 명시되면 함께 저장된다")
        void withAssignees_savesBoth() {
            Long currentUserId = 1L;
            TeamMember tester = memberMock(1L, "테스터");
            TeamMember a1 = memberMock(2L, "담당1");
            TeamMember a2 = memberMock(3L, "담당2");
            when(updateRepository.findById(1L)).thenReturn(Optional.of(update));
            when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(tester));
            when(memberRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(a1));
            when(memberRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(a2));
            when(qaRepository.save(any(QaItem.class))).thenAnswer(inv -> inv.getArgument(0));

            QaDto.CreateRequest req = new QaDto.CreateRequest(
                1L, "t", null, null, QaStatus.NEEDS_FIX,
                null, 2L, 3L, QaPriority.MEDIUM, List.of(), null, null, null
            );

            service.create(req, currentUserId);

            ArgumentCaptor<QaItem> cap = ArgumentCaptor.forClass(QaItem.class);
            verify(qaRepository).save(cap.capture());
            assertThat(cap.getValue().getAssignee1()).isSameAs(a1);
            assertThat(cap.getValue().getAssignee2()).isSameAs(a2);
        }
    }

    @Nested
    @DisplayName("update — 멤버 슬롯 3개 독립 처리")
    class UpdateMemberSlots {

        private QaItem existing;
        private TeamMember currentChangedBy;

        @BeforeEach
        void setupExisting() {
            existing = mock(QaItem.class);
            currentChangedBy = memberMock(99L, "변경자");

            // 기본 상태: title/desc/category/status/priority 변경 없음 가정
            lenient().when(existing.getId()).thenReturn(500L);
            lenient().when(existing.getTitle()).thenReturn("기존제목");
            lenient().when(existing.getDescription()).thenReturn("기존본문");
            lenient().when(existing.getCategory()).thenReturn("기존카테고리");
            lenient().when(existing.getStatus()).thenReturn(QaStatus.NEEDS_FIX);
            lenient().when(existing.getPriority()).thenReturn(QaPriority.MEDIUM);
            lenient().when(existing.getProjectUpdate()).thenReturn(update);

            when(qaRepository.findById(500L)).thenReturn(Optional.of(existing));
            when(memberRepository.findByIdAndDeletedAtIsNull(99L))
                .thenReturn(Optional.of(currentChangedBy));
        }

        private QaDto.UpdateRequest reqOnlyMembers(Long testerId, Long a1Id, Long a2Id,
                                                  Boolean clearT, Boolean clearA1, Boolean clearA2) {
            return new QaDto.UpdateRequest(
                null,              // updateId
                null, null, null,  // title/desc/category
                null,              // status
                testerId, a1Id, a2Id,
                null,              // priority
                null,              // images
                clearT, clearA1, clearA2
            );
        }

        @Test
        @DisplayName("같은 ID 를 다시 보내면 변경 없음 (이력 없음, setter 호출 없음)")
        void sameId_noChange() {
            TeamMember existingA1 = memberMock(2L, "기존담당1");
            when(existing.getAssignee1()).thenReturn(existingA1);

            service.update(500L, reqOnlyMembers(null, 2L, null, null, null, null), 99L);

            // 변경 감지가 일어나지 않았으므로 setter 호출 없음
            verify(existing, never()).setAssignee1(any());
            verifyNoInteractions(historyRepository);
            verify(events, never()).publishEvent(any(QaItemService.QaAssigneeChangedEvent.class));
        }

        @Test
        @DisplayName("새 ID 를 지정하면 setter 호출 + 이력 저장 + assignee 변경 이벤트 발행")
        void newAssignee1_setsAndPublishesEvent() {
            when(existing.getAssignee1()).thenReturn(null);
            TeamMember newA1 = memberMock(2L, "신규담당1");
            when(memberRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(newA1));

            service.update(500L, reqOnlyMembers(null, 2L, null, null, null, null), 99L);

            verify(existing).setAssignee1(newA1);

            ArgumentCaptor<List<QaHistory>> diffsCap = ArgumentCaptor.forClass(List.class);
            verify(historyRepository).saveAll(diffsCap.capture());
            assertThat(diffsCap.getValue()).hasSize(1);
            assertThat(diffsCap.getValue().get(0).getField()).isEqualTo("assignee1");

            verify(events).publishEvent(any(QaItemService.QaAssigneeChangedEvent.class));
        }

        @Test
        @DisplayName("clearAssignee1=true 면 null 로 설정 + 이력 기록 (이벤트는 없음)")
        void clearAssignee1_setsNull_logsHistory_noEvent() {
            TeamMember existingA1 = memberMock(2L, "기존담당1");
            when(existing.getAssignee1()).thenReturn(existingA1);

            service.update(500L, reqOnlyMembers(null, null, null, null, true, null), 99L);

            verify(existing).setAssignee1(null);

            ArgumentCaptor<List<QaHistory>> diffsCap = ArgumentCaptor.forClass(List.class);
            verify(historyRepository).saveAll(diffsCap.capture());
            assertThat(diffsCap.getValue()).hasSize(1);
            assertThat(diffsCap.getValue().get(0).getField()).isEqualTo("assignee1");
            assertThat(diffsCap.getValue().get(0).getOldValue()).isEqualTo("기존담당1");
            assertThat(diffsCap.getValue().get(0).getNewValue()).isNull();

            verify(events, never()).publishEvent(any(QaItemService.QaAssigneeChangedEvent.class));
        }

        @Test
        @DisplayName("tester 변경도 이력 기록 + 배정 알림 이벤트를 발행한다")
        void testerChange_publishesNotificationEvent() {
            when(existing.getTester()).thenReturn(null);
            TeamMember newTester = memberMock(5L, "새테스터");
            when(memberRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(newTester));

            service.update(500L, reqOnlyMembers(5L, null, null, null, null, null), 99L);

            verify(existing).setTester(newTester);

            ArgumentCaptor<List<QaHistory>> diffsCap = ArgumentCaptor.forClass(List.class);
            verify(historyRepository).saveAll(diffsCap.capture());
            assertThat(diffsCap.getValue()).hasSize(1);
            assertThat(diffsCap.getValue().get(0).getField()).isEqualTo("tester");

            // tester 슬롯도 notify=true — 이전엔 notify=false 라 테스터만 알림이 누락됐음.
            ArgumentCaptor<QaItemService.QaAssigneeChangedEvent> evCap =
                ArgumentCaptor.forClass(QaItemService.QaAssigneeChangedEvent.class);
            verify(events).publishEvent(evCap.capture());
            assertThat(evCap.getValue().assigneeMemberId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("어떤 필드도 안 보내면 setter / 이력 / 이벤트 모두 호출 없음")
        void noChanges_doesNothing() {
            service.update(500L, reqOnlyMembers(null, null, null, null, null, null), 99L);

            verify(existing, never()).setTester(any());
            verify(existing, never()).setAssignee1(any());
            verify(existing, never()).setAssignee2(any());
            verifyNoInteractions(historyRepository);
            verifyNoInteractions(events);
        }
    }

    @Nested
    @DisplayName("update — 상태 변경 이벤트")
    class UpdateStatusEvent {

        @Test
        @DisplayName("상태 변경 시 tester/assignee1/assignee2 모두를 담은 이벤트가 발행된다")
        void statusChange_eventContainsAllRecipients() {
            QaItem existing = mock(QaItem.class);
            TeamMember tester = memberMock(2L, "테스터");
            TeamMember a1 = memberMock(3L, "담당1");
            TeamMember a2 = memberMock(4L, "담당2");
            lenient().when(existing.getId()).thenReturn(500L);
            lenient().when(existing.getTitle()).thenReturn("t");
            // mock 은 setter 호출해도 상태가 안 바뀌므로 항상 같은 값 반환.
            // 변경 감지는 req.status (IN_PROGRESS) != getStatus (NEEDS_FIX) 로 통과한다.
            lenient().when(existing.getStatus()).thenReturn(QaStatus.NEEDS_FIX);
            lenient().when(existing.getPriority()).thenReturn(QaPriority.MEDIUM);
            lenient().when(existing.getImages()).thenReturn(List.of());
            lenient().when(existing.getProjectUpdate()).thenReturn(update);
            lenient().when(existing.getTester()).thenReturn(tester);
            lenient().when(existing.getAssignee1()).thenReturn(a1);
            lenient().when(existing.getAssignee2()).thenReturn(a2);

            when(qaRepository.findById(500L)).thenReturn(Optional.of(existing));
            when(memberRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

            QaDto.UpdateRequest req = new QaDto.UpdateRequest(
                null,
                null, null, null,
                QaStatus.IN_PROGRESS,
                null, null, null,
                null, null, null, null, null
            );

            service.update(500L, req, 99L);

            // 핵심 검증: 이벤트에 actor 외에 tester/assignee1/assignee2 모두 담겼다.
            ArgumentCaptor<QaItemService.QaStatusChangedEvent> evCap =
                ArgumentCaptor.forClass(QaItemService.QaStatusChangedEvent.class);
            verify(events).publishEvent(evCap.capture());
            QaItemService.QaStatusChangedEvent ev = evCap.getValue();
            assertThat(ev.testerMemberId()).isEqualTo(2L);
            assertThat(ev.assignee1MemberId()).isEqualTo(3L);
            assertThat(ev.assignee2MemberId()).isEqualTo(4L);
            assertThat(ev.actorMemberId()).isEqualTo(99L);
        }
    }
}
