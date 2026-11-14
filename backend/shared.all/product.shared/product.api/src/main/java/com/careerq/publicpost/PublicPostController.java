package com.careerq.publicpost;

import com.careerq.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PublicPostController {

    private final PublicPostRepository publicPostRepository;

    public PublicPostController(PublicPostRepository publicPostRepository) {
        this.publicPostRepository = publicPostRepository;
    }

    public record PostView(Long id, Long originalQuestionId, String title, String questionBody,
                           String hrAnswer, boolean anonymized, LocalDateTime publishedAt) {
    }

    @GetMapping("/posts")
    public ApiResponse<List<PostView>> list() {
        return ApiResponse.ok(publicPostRepository.findByStatusOrderByPublishedAtDesc("published")
                .stream().map(this::toView).toList());
    }

    @GetMapping("/feed/highlights")
    public ApiResponse<List<PostView>> highlights() {
        return ApiResponse.ok(publicPostRepository.findByStatusOrderByPublishedAtDesc("published")
                .stream().limit(10).map(this::toView).toList());
    }

    private PostView toView(PublicPost post) {
        return new PostView(post.getId(), post.getOriginalQuestionId(), post.getTitle(),
                post.getQuestionBody(), post.getHrAnswer(), post.isAnonymized(), post.getPublishedAt());
    }
}
