package com.qamanager.notification.teams;

/**
 * Teams 테스트 발송 결과. UI 가 단계별 진단을 표시할 수 있도록 모든 step 의 통과 여부를 담는다.
 */
public record TestSendResult(
    boolean success,
    String errorMessage,
    String memberName,
    String email,
    boolean configOk,
    boolean notifyEnabled,
    boolean aadMapped,
    String teamsUserId,
    boolean chatOk,
    String chatId,
    boolean sent
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String errorMessage;
        private String memberName;
        private String email;
        private boolean configOk;
        private boolean notifyEnabled;
        private boolean aadMapped;
        private String teamsUserId;
        private boolean chatOk;
        private String chatId;
        private boolean sent;

        public Builder configOk(boolean v) { this.configOk = v; return this; }
        public Builder memberName(String v) { this.memberName = v; return this; }
        public Builder email(String v) { this.email = v; return this; }
        public Builder notifyEnabled(boolean v) { this.notifyEnabled = v; return this; }
        public Builder aadMapped(boolean v) { this.aadMapped = v; return this; }
        public Builder teamsUserId(String v) { this.teamsUserId = v; return this; }
        public Builder chatOk(boolean v) { this.chatOk = v; return this; }
        public Builder chatId(String v) { this.chatId = v; return this; }
        public Builder sent(boolean v) { this.sent = v; return this; }
        public Builder fail(String msg) { this.errorMessage = msg; return this; }

        public TestSendResult build() {
            return new TestSendResult(
                sent && errorMessage == null,
                errorMessage,
                memberName, email,
                configOk, notifyEnabled, aadMapped, teamsUserId,
                chatOk, chatId, sent
            );
        }
    }
}
