package com.qamanager.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class FileDto {

    public record PresignRequest(
        @NotBlank @Size(max = 200) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        /** qa_image | comment_image | avatar */
        @NotBlank @Pattern(regexp = "qa_image|comment_image|avatar") String purpose
    ) {}

    public record PresignResponse(
        String key,
        String uploadUrl,
        String publicUrl,
        long expiresInSeconds
    ) {}
}
