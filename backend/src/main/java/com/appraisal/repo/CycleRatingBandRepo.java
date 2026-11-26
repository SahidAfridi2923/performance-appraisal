package com.appraisal.repo;

import com.appraisal.domain.CycleRatingBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CycleRatingBandRepo extends JpaRepository<CycleRatingBand, Long> {
    List<CycleRatingBand> findByCycleIdOrderByMinScoreAsc(Long cycleId);
    void deleteByCycleId(Long cycleId);
}
