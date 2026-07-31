package com.qamanager.integration.github;

import com.qamanager.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GitHub App 수명주기 관리 — Manifest flow 로 앱 생성, 자격증명 보관, 설치 repo 조회.
 *
 * Manifest flow:
 *  1) buildManifest() 로 manifest JSON 생성 → 프론트가 GitHub 에 form POST
 *  2) 사용자가 GitHub 에서 앱 생성을 승인 → redirect_url 로 code 전달
 *  3) convert(code) 가 자격증명(app id / private key)을 교환해 DB 에 저장
 */
@Service
public class GithubAppService {

    private final GithubAppRepository appRepository;
    private final GithubClient client;
    private final GithubProperties props;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public GithubAppService(GithubAppRepository appRepository, GithubClient client, GithubProperties props) {
        this.appRepository = appRepository;
        this.client = client;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public GithubDto.AppStatus status() {
        return appRepository.findTopByOrderByIdAsc()
            .map(this::toStatus)
            .orElseGet(() -> new GithubDto.AppStatus(false, null, null, null));
    }

    public GithubDto.ManifestResponse buildManifest(String organization, String baseUrl) {
        String base = trimSlash(baseUrl);
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw ApiException.badRequest("baseUrl 은 http(s) origin 이어야 합니다.");
        }
        String targetUrl = (organization != null && !organization.isBlank())
            ? props.effectiveWebBaseUrl() + "/organizations/" + organization.trim() + "/settings/apps/new"
            : props.effectiveWebBaseUrl() + "/settings/apps/new";

        ObjectNode m = mapper.createObjectNode();
        m.put("name", "qa-manager");
        m.put("url", base);
        // 앱 생성 승인 후 GitHub 이 code 쿼리와 함께 돌려보내는 주소 (프론트 설정 화면).
        m.put("redirect_url", base + "/admin?tab=settings&sub=github");
        m.put("public", false);
        // 웹훅은 1차(단방향) 범위 밖 — 비활성으로 선언만 해둔다.
        ObjectNode hook = m.putObject("hook_attributes");
        hook.put("url", base + "/api/github/webhook");
        hook.put("active", false);
        ObjectNode perms = m.putObject("default_permissions");
        perms.put("issues", "write");
        perms.put("metadata", "read");
        perms.put("contents", "read");
        m.putArray("default_events");

        return new GithubDto.ManifestResponse(targetUrl, mapper.writeValueAsString(m));
    }

    @Transactional
    public GithubDto.AppStatus convert(String code) {
        GithubDto.ManifestConversion conv = client.convertManifest(code);
        // 단일 앱 운용 — 재연동 시 기존 자격증명 교체.
        appRepository.deleteAll();
        GithubApp saved = appRepository.save(new GithubApp(
            conv.appId(), conv.slug(), conv.name(), conv.htmlUrl(),
            conv.clientId(), conv.clientSecret(), conv.webhookSecret(), conv.pem()));
        return toStatus(saved);
    }

    @Transactional
    public void disconnect() {
        appRepository.deleteAll();
    }

    // 주의: GitHub API 다회 호출 구간이라 트랜잭션(DB 커넥션 점유) 없이 동작한다.
    public List<GithubDto.Repo> listRepos() {
        GithubApp app = requireApp();
        List<GithubDto.Repo> repos = new ArrayList<>();
        for (Long installationId : client.listInstallationIds(app)) {
            repos.addAll(client.listInstallationRepos(app, installationId));
        }
        return repos;
    }

    @Transactional(readOnly = true)
    public Optional<GithubApp> findApp() {
        return appRepository.findTopByOrderByIdAsc();
    }

    private GithubApp requireApp() {
        return appRepository.findTopByOrderByIdAsc()
            .orElseThrow(() -> ApiException.badRequest("GitHub App 이 설정되지 않았습니다. 관리자 설정 > GitHub 에서 먼저 연동하세요."));
    }

    private GithubDto.AppStatus toStatus(GithubApp app) {
        String installUrl = props.effectiveWebBaseUrl() + "/apps/" + app.getAppSlug() + "/installations/new";
        return new GithubDto.AppStatus(true, app.getAppSlug(), app.getAppName(), installUrl);
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
