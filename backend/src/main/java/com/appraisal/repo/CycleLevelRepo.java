package com.appraisal.repo;

import com.appraisal.domain.CycleLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CycleLevelRepo extends JpaRepository<CycleLevel, Long> {
    List<CycleLevel> findByCycleIdOrderBySortOrderAsc(Long cycleId);
}
