package com.qamanager.qa.comment;

import com.qamanager.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QaCommentController {

    private final QaCommentService commentService;

    public QaCommentController(QaCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/qa/{qaId}/comments")
    public List<CommentDto.Response> list(@PathVariable Long qaId) {
        return commentService.list(qaId);
    }

    @PostMapping("/qa/{qaId}/comments")
    public CommentDto.Response create(@PathVariable Long qaId, @RequestBody @Valid CommentDto.CreateRequest req) {
        return commentService.create(qaId, req, CurrentUser.getIdOrThrow());
    }

    @PatchMapping("/comments/{id}")
    public CommentDto.Response update(@PathVariable Long id, @RequestBody @Valid CommentDto.UpdateRequest req) {
        return commentService.update(id, req, CurrentUser.getIdOrThrow());
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id, CurrentUser.getIdOrThrow());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{id}/reactions")
    public CommentDto.Response toggleReaction(@PathVariable Long id,
                                              @RequestBody @Valid CommentDto.ReactionRequest req) {
        return commentService.toggleReaction(id, req.emoji(), CurrentUser.getIdOrThrow());
    }
}
