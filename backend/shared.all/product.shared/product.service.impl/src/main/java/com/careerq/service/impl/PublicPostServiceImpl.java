package com.careerq.service.impl;

import com.careerq.dao.PublicPostDao;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.security.AuthUser;
import com.careerq.service.PublicPostService;
import com.careerq.service.impl.ContentReadSupport.InteractionStates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublicPostServiceImpl implements PublicPostService {

    private final PublicPostDao publicPostDao;
    private final ContentReadSupport contentReadSupport;

    public PublicPostServiceImpl(PublicPostDao publicPostDao, ContentReadSupport contentReadSupport) {
        this.publicPostDao = publicPostDao;
        this.contentReadSupport = contentReadSupport;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> list(AuthUser viewer) {
        return toViews(publicPostDao.findPublished(null), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> highlights(AuthUser viewer) {
        return toViews(publicPostDao.findPublished(10), viewer);
    }

    private List<PostView> toViews(List<PublicPostDO> posts, AuthUser viewer) {
        Long viewerId = viewer == null ? null : viewer.getId();
        InteractionStates states = contentReadSupport.interactions(viewerId, "post",
                posts.stream().map(PublicPostDO::getId).toList());
        return posts.stream().map(post -> toView(post, states)).toList();
    }

    private PostView toView(PublicPostDO post, InteractionStates states) {
        return new PostView(post.getId(), post.getOriginalQuestionId(), post.getTitle(),
                post.getQuestionBody(), post.getHrAnswer(), post.isAnonymized(), post.getPublishedAt(),
                states.has(post.getId(), "like"), states.has(post.getId(), "favorite"));
    }
}
