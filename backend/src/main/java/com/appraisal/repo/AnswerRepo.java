package com.appraisal.repo;

import com.appraisal.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AnswerRepo extends JpaRepository<Answer, Long> {
    @Query("select a from Answer a join fetch a.question where a.review.id in :reviewIds")
    List<Answer> findByReviewIds(Collection<Long> reviewIds);

    long countByQuestionId(Long cycleQuestionId);

    @Modifying
    @Query("delete from Answer a where a.question.id = :cycleQuestionId")
    void deleteByQuestionId(Long cycleQuestionId);
}
