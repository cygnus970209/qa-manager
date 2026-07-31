package com.qamanager.integration.github;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/github")
public class GithubController {

    private final GithubAppService appService;
    private final GithubIssueService issueService;

    public GithubController(GithubAppService appService, GithubIssueService issueService) {
        this.appService = appService;
        this.issueService = issueService;
    }

    @GetMapping("/app")
    public GithubDto.AppStatus status() {
        return appService.status();
    }

    @PostMapping("/app/manifest")
    public GithubDto.ManifestResponse manifest(@RequestBody @Valid GithubDto.ManifestRequest req) {
        return appService.buildManifest(req.organization(), req.baseUrl());
    }

    @PostMapping("/app/conversion")
    public GithubDto.AppStatus convert(@RequestBody @Valid GithubDto.ConversionRequest req) {
        return appService.convert(req.code());
    }

    @DeleteMapping("/app")
    public ResponseEntity<Void> disconnect() {
        appService.disconnect();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/repos")
    public List<GithubDto.Repo> repos() {
        return appService.listRepos();
    }

    @GetMapping("/qa/{qaId}/commits")
    public List<GithubDto.Commit> commits(@PathVariable Long qaId) {
        return issueService.listCommits(qaId);
    }
}
