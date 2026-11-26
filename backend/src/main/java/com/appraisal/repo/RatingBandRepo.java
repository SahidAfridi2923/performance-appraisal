package com.appraisal.repo;

import com.appraisal.domain.RatingBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RatingBandRepo extends JpaRepository<RatingBand, Long> {
    List<RatingBand> findAllByOrderByMinScoreAsc();
}
