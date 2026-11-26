package com.appraisal.repo;

import com.appraisal.domain.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepo extends JpaRepository<Achievement, Long> {
    List<Achievement> findByAppraisalIdOrderBySortOrderAscIdAsc(Long appraisalId);
    long countByAppraisalIdAndVerifiedTrue(Long appraisalId);
}
