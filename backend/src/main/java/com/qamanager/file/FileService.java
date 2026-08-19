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
    /** 아바타를 제외한 QA/댓글 첨부에서 이미지 외에 추가로 허용하는 타입. */
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of("application/pdf");
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    private final S3Properties props;
    private final S3Presigner presigner;

    public FileService(S3Properties props, S3Presigner presigner) {
        this.props = props;
        this.presigner = presigner;
    }

    public FileDto.PresignResponse presignUpload(FileDto.PresignRequest req) {
        String contentType = req.contentType().toLowerCase();
        boolean allowed = ALLOWED_IMAGE_TYPES.contains(contentType)
            || (!"avatar".equals(req.purpose()) && ALLOWED_DOCUMENT_TYPES.contains(contentType));
        if (!allowed) {
            throw ApiException.badRequest("허용되지 않은 contentType: " + req.contentType());
        }
        if (req.fileSize() > MAX_FILE_SIZE_BYTES) {
            throw ApiException.badRequest("파일 크기는 100MB 이하만 업로드할 수 있습니다.");
        }
        String safeName = sanitize(req.fileName());
        String key = "qa-manager/" + req.purpose() + "/" + UUID.randomUUID() + "-" + safeName;

        // contentLength 를 서명에 포함해 선언한 크기와 다른 PUT 은 S3 가 거부하게 한다.
        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(props.bucket())
            .key(key)
            .contentType(req.contentType())
            .contentLength(req.fileSize())
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
