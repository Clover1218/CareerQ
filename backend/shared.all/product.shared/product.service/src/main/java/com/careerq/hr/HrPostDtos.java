package com.careerq.hr;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class HrPostDtos {
    private HrPostDtos() {}
    public record CreateDraftRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{16,64}") String clientKey,
            @NotNull @Size(max = 200) String title,
            @NotNull @Size(max = 10000) String body,
            @NotNull @Size(max = 5) List<@NotBlank @Size(max = 32) String> tags) {}
    public record SaveDraftRequest(
            @NotNull @Positive Long version,
            @NotNull @Size(max = 200) String title,
            @NotNull @Size(max = 10000) String body,
            @NotNull @Size(max = 5) List<@NotBlank @Size(max = 32) String> tags) {}
    public record VersionRequest(@NotNull @Positive Long version) {}
    public record HrPostView(Long id, String title, String body, List<String> tags, String status,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime submittedAt) {}
    public record HrPostSummary(Long id, String title, String excerpt, List<String> tags, String status,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime submittedAt) {}
    public record PublicShareSummary(Long id, String title, String excerpt, List<String> tags,
            Long authorId, String authorNickname, String authorAvatarUrl, LocalDateTime publishedAt) {}
    public record PublicShareDetail(Long id, String title, String body, List<String> tags,
            Long authorId, String authorNickname, String authorAvatarUrl, LocalDateTime publishedAt) {}
}
