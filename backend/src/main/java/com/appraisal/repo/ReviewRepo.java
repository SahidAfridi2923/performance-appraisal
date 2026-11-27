package com.appraisal.repo;

import com.appraisal.domain.Enums;
import com.appraisal.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepo extends JpaRepository<Review, Long> {
    List<Review> findByAppraisalId(Long appraisalId);
    boolean existsByAppraisalId(Long appraisalId);
    Optional<Review> findByAppraisalIdAndReviewerIdAndRole(Long appraisalId, Long reviewerId, Enums.ReviewerRole role);

    @Query("select r from Review r where r.appraisal.cycle.id = :cycleId")
    List<Review> findByCycle(Long cycleId);

    @Query("select r from Review r where r.appraisal.id in :appraisalIds")
    List<Review> findByAppraisalIds(Collection<Long> appraisalIds);
}
