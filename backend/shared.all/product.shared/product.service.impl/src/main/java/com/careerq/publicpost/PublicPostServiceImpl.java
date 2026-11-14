package com.careerq.publicpost;

import com.careerq.service.PublicPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublicPostServiceImpl implements PublicPostService {

    private final PublicPostRepository publicPostRepository;

    public PublicPostServiceImpl(PublicPostRepository publicPostRepository) {
        this.publicPostRepository = publicPostRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> list() {
        return publicPostRepository.findByStatusOrderByPublishedAtDesc("published")
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> highlights() {
        return publicPostRepository.findByStatusOrderByPublishedAtDesc("published")
                .stream().limit(10).map(this::toView).toList();
    }

    private PostView toView(PublicPost post) {
        return new PostView(post.getId(), post.getOriginalQuestionId(), post.getTitle(),
                post.getQuestionBody(), post.getHrAnswer(), post.isAnonymized(), post.getPublishedAt());
    }
}
