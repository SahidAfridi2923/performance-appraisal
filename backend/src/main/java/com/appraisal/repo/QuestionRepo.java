package com.appraisal.repo;

import com.appraisal.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepo extends JpaRepository<Question, Long> {
    List<Question> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);
    long countByCategoryId(Long categoryId);
}
