package com.qamanager.file;

import com.qamanager.common.ApiException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/avif"
    );

    private final S3Properties props;
    private final S3Presigner presigner;

    public FileService(S3Properties props, S3Presigner presigner) {
        this.props = props;
        this.presigner = presigner;
    }

    public FileDto.PresignResponse presignUpload(FileDto.PresignRequest req) {
        if (!ALLOWED_IMAGE_TYPES.contains(req.contentType().toLowerCase())) {
            throw ApiException.badRequest("허용되지 않은 contentType: " + req.contentType());
        }
        String safeName = sanitize(req.fileName());
        String key = "qa-manager/" + req.purpose() + "/" + UUID.randomUUID() + "-" + safeName;

        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(props.bucket())
            .key(key)
            .contentType(req.contentType())
            .build();

        PutObjectPresignRequest presign = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(props.presignTtlSeconds()))
            .putObjectRequest(put)
            .build();

        String uploadUrl = presigner.presignPutObject(presign).url().toString();
        String publicUrl = "https://" + props.bucket() + ".s3." + props.region() + ".amazonaws.com/"
            + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");

        return new FileDto.PresignResponse(key, uploadUrl, publicUrl, props.presignTtlSeconds());
    }

    private String sanitize(String name) {
        // 경로 분리자 제거 + 공백 → '_'
        String base = name.replace('/', '_').replace('\\', '_').trim();
        return base.isEmpty() ? "upload" : base.replaceAll("\\s+", "_");
    }
}
