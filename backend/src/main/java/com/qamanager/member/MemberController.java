package com.qamanager.member;

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

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberDto.Response> list() {
        return memberService.list();
    }

    @GetMapping("/{id}")
    public MemberDto.Response get(@PathVariable Long id) {
        return memberService.get(id);
    }

    @PostMapping
    public ResponseEntity<MemberDto.Response> create(@RequestBody @Valid MemberDto.CreateRequest req) {
        MemberDto.Response created = memberService.create(req);
        return ResponseEntity.created(URI.create("/api/members/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public MemberDto.Response update(@PathVariable Long id, @RequestBody @Valid MemberDto.UpdateRequest req) {
        return memberService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
