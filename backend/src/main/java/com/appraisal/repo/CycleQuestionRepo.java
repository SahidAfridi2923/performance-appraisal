package com.appraisal.repo;

import com.appraisal.domain.CycleQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CycleQuestionRepo extends JpaRepository<CycleQuestion, Long> {
    @Query("select q from CycleQuestion q where q.category.cycle.id = :cycleId order by q.category.sortOrder, q.sortOrder, q.id")
    List<CycleQuestion> findByCycle(Long cycleId);
    List<CycleQuestion> findByCategoryId(Long cycleCategoryId);
    boolean existsBySourceQuestionId(Long sourceQuestionId);
}
