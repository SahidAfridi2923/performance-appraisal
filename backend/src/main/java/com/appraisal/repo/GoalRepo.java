package com.appraisal.repo;

import com.appraisal.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepo extends JpaRepository<Goal, Long> {
    List<Goal> findByAppraisalIdOrderBySortOrderAscIdAsc(Long appraisalId);
    long countByAppraisalId(Long appraisalId);
}
