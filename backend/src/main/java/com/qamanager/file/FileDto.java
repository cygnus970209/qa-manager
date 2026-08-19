package com.qamanager.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class FileDto {

    public record PresignRequest(
        @NotBlank @Size(max = 200) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        /** qa_image | comment_image | avatar */
        @NotBlank @Pattern(regexp = "qa_image|comment_image|avatar") String purpose,
        /** 업로드할 파일의 바이트 크기 — presign 시 Content-Length 로 서명되어 초과 업로드를 차단한다. */
        @NotNull @Positive Long fileSize
    ) {}

    public record PresignResponse(
        String key,
        String uploadUrl,
        String publicUrl,
        long expiresInSeconds
    ) {}
}
