package com.qamanager.integration.github;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Manifest flow 로 생성된 GitHub App 자격증명 (단일 행 운용).
 * 셀프호스팅 특성상 앱이 런타임에 만들어지므로 .env 가 아닌 DB 에 보관한다.
 */
@Entity
@Table(name = "github_app")
public class GithubApp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "app_slug", nullable = false, length = 100)
    private String appSlug;

    @Column(name = "app_name", length = 200)
    private String appName;

    @Column(name = "html_url", length = 500)
    private String htmlUrl;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    @Column(name = "webhook_secret", length = 200)
    private String webhookSecret;

    /** RSA private key (manifest conversion 응답의 PEM 원문). */
    @Column(name = "pem", nullable = false, columnDefinition = "TEXT")
    private String pem;

    protected GithubApp() {}

    public GithubApp(Long appId, String appSlug, String appName, String htmlUrl,
                     String clientId, String clientSecret, String webhookSecret, String pem) {
        this.appId = appId;
        this.appSlug = appSlug;
        this.appName = appName;
        this.htmlUrl = htmlUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.webhookSecret = webhookSecret;
        this.pem = pem;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getAppSlug() { return appSlug; }
    public String getAppName() { return appName; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getWebhookSecret() { return webhookSecret; }
    public String getPem() { return pem; }
}
