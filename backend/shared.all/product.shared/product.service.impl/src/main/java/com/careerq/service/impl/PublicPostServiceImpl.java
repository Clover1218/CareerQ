package com.careerq.service.impl;

import com.careerq.dao.InteractionDao;
import com.careerq.dao.PublicPostDao;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.security.AuthUser;
import com.careerq.service.PublicPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublicPostServiceImpl implements PublicPostService {

    private final PublicPostDao publicPostDao;
    private final InteractionDao interactionDao;

    public PublicPostServiceImpl(PublicPostDao publicPostDao, InteractionDao interactionDao) {
        this.publicPostDao = publicPostDao;
        this.interactionDao = interactionDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> list(AuthUser viewer) {
        return publicPostDao.findPublished(null).stream().map(post -> toView(post, viewer)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> highlights(AuthUser viewer) {
        return publicPostDao.findPublished(10).stream().map(post -> toView(post, viewer)).toList();
    }

    private PostView toView(PublicPostDO post, AuthUser viewer) {
        Long viewerId = viewer == null ? null : viewer.getId();
        boolean liked = viewerId != null && interactionDao.exists(viewerId, "post", post.getId(), "like");
        boolean favorited = viewerId != null && interactionDao.exists(viewerId, "post", post.getId(), "favorite");
        return new PostView(post.getId(), post.getOriginalQuestionId(), post.getTitle(),
                post.getQuestionBody(), post.getHrAnswer(), post.isAnonymized(), post.getPublishedAt(),
                liked, favorited);
    }
}