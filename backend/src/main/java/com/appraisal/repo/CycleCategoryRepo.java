package com.appraisal.repo;

import com.appraisal.domain.CycleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CycleCategoryRepo extends JpaRepository<CycleCategory, Long> {
    List<CycleCategory> findByCycleIdOrderBySortOrderAsc(Long cycleId);
}
