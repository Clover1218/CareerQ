package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.UserBrief;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.RoundtableDao;
import com.careerq.dao.RoundtableDao.PublishedAnswer;
import com.careerq.dao.TagDao;
import com.careerq.dataobject.QuestionDO;
import com.careerq.roundtable.RoundtableDtos.Candidate;
import com.careerq.roundtable.RoundtableDtos.Featured;
import com.careerq.roundtable.RoundtableDtos.Roundtable;
import com.careerq.roundtable.RoundtableDtos.Seat;
import com.careerq.roundtable.RoundtableDtos.TopicChoice;
import com.careerq.security.AuthUser;
import com.careerq.service.RoundtableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class RoundtableServiceImpl implements RoundtableService {

    private static final ZoneId ROUND_ZONE = ZoneId.of("Asia/Shanghai");

    private final RoundtableDao dao;
    private final QuestionDao questions;
    private final TagDao tags;

    public RoundtableServiceImpl(RoundtableDao dao, QuestionDao questions, TagDao tags) {
        this.dao = dao;
        this.questions = questions;
        this.tags = tags;
    }

    @Override
    @Transactional(readOnly = true)
    public Roundtable current(AuthUser user) {
        return snapshot(user, currentWeek());
    }

    @Override
    @Transactional(readOnly = true)
    public Featured featured() {
        return featuredForWeek(currentWeek());
    }

    @Override
    @Transactional
    public Roundtable nominate(AuthUser student, TopicChoice choice) {
        LocalDate week = activeBallot(choice);
        lockPublicQuestion(choice.questionId());
        dao.nominate(week, choice.questionId());
        return snapshot(student, week);
    }

    @Override
    @Transactional
    public Roundtable vote(AuthUser student, TopicChoice choice) {
        LocalDate week = activeBallot(choice);
        lockPublicQuestion(choice.questionId());
        if (dao.hasNomination(week, choice.questionId()) == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "这道问题还未加入本期投票");
        }
        dao.vote(week, student.getId(), choice.questionId());
        return snapshot(student, week);
    }

    private Roundtable snapshot(AuthUser user, LocalDate week) {
        List<Candidate> candidates = dao.candidates(week, Integer.MAX_VALUE);
        Long myVote = user != null && "student".equals(user.getRole()) ? dao.findVote(week, user.getId()) : null;
        return new Roundtable(week, week.plusWeeks(1), featuredForWeek(week), candidates, myVote);
    }

    private Featured featuredForWeek(LocalDate week) {
        List<Candidate> previous = dao.candidates(week.minusWeeks(1), 1);
        boolean selectedByVote = !previous.isEmpty() && previous.getFirst().votes() > 0;
        Long questionId = selectedByVote ? Long.valueOf(previous.getFirst().questionId()) : dao.fallbackQuestionId();
        if (questionId == null) return null;
        return questions.findById(questionId)
                .filter(this::isPublicQuestion)
                .map(question -> buildFeatured(question, selectedByVote)).orElse(null);
    }

    private Featured buildFeatured(QuestionDO question, boolean selectedByVote) {
        List<PublishedAnswer> authors = dao.publishedAuthors(question.getId());
        var companies = new HashSet<String>();
        var selected = new ArrayList<PublishedAnswer>();
        for (PublishedAnswer answer : authors) {
            if (answer.company() != null && !answer.company().isBlank()
                    && companies.add(answer.company().strip().toLowerCase(Locale.ROOT))) {
                selected.add(answer);
                if (selected.size() == 3) break;
            }
        }
        for (PublishedAnswer answer : authors) {
            if (selected.size() == 3) break;
            if (!selected.contains(answer)) selected.add(answer);
        }
        List<Seat> seats = selected.stream().map(answer -> new Seat(answer.answerId(),
                new UserBrief(answer.authorId(), answer.nickname(), answer.company(),
                        answer.position(), answer.avatarUrl()))).toList();
        long answerCount = authors.stream().mapToLong(PublishedAnswer::authorAnswerCount).sum();
        return new Featured(question.getId(), question.getTitle(), tags.findNamesByQuestionId(question.getId()),
                selectedByVote, answerCount, authors.size(), seats);
    }

    private void lockPublicQuestion(long id) {
        if (questions.findByIdForUpdate(id).filter(this::isPublicQuestion).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "只可以推荐或投票给已发布的公开问题");
        }
    }

    private boolean isPublicQuestion(QuestionDO question) {
        return "public".equals(question.getScope()) && "VISIBLE".equals(question.getVisibility());
    }

    private LocalDate activeBallot(TopicChoice choice) {
        LocalDate week = currentWeek();
        if (!week.equals(choice.week())) {
            throw new BusinessException(ErrorCode.CONFLICT, "共议已换期，请刷新后重试");
        }
        return week;
    }

    private LocalDate currentWeek() {
        return LocalDate.now(ROUND_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
