package com.appraisal.repo;

import com.appraisal.domain.Category;
import com.appraisal.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepo extends JpaRepository<Category, Long> {
    List<Category> findByBlockOrderBySortOrderAsc(Enums.Block block);
    List<Category> findByBlockAndTeamIdOrderBySortOrderAsc(Enums.Block block, Long teamId);
}
