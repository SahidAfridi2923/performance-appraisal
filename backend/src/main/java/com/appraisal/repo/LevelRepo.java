package com.appraisal.repo;

import com.appraisal.domain.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelRepo extends JpaRepository<Level, Long> {
    List<Level> findAllByOrderBySortOrderAsc();
    Optional<Level> findByName(String name);
}
