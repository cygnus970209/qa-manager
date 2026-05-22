package com.qamanager.qa.comment;

import java.io.Serializable;
import java.util.Objects;

public class QaCommentReactionId implements Serializable {

    private Long comment;
    private String emoji;
    private Long member;

    public QaCommentReactionId() {}

    public QaCommentReactionId(Long comment, String emoji, Long member) {
        this.comment = comment;
        this.emoji = emoji;
        this.member = member;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QaCommentReactionId that)) return false;
        return Objects.equals(comment, that.comment)
            && Objects.equals(emoji, that.emoji)
            && Objects.equals(member, that.member);
    }

    @Override
    public int hashCode() { return Objects.hash(comment, emoji, member); }
}
