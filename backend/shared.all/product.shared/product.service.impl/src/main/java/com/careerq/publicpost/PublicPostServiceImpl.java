package com.careerq.publicpost;

import com.careerq.dao.PublicPostDao;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.service.PublicPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublicPostServiceImpl implements PublicPostService {

    private final PublicPostDao publicPostDao;

    public PublicPostServiceImpl(PublicPostDao publicPostDao) {
        this.publicPostDao = publicPostDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> list() {
        return publicPostDao.findPublished(null)
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostView> highlights() {
        return publicPostDao.findPublished(10).stream().map(this::toView).toList();
    }

    private PostView toView(PublicPostDO post) {
        return new PostView(post.getId(), post.getOriginalQuestionId(), post.getTitle(),
                post.getQuestionBody(), post.getHrAnswer(), post.isAnonymized(), post.getPublishedAt());
    }
}
