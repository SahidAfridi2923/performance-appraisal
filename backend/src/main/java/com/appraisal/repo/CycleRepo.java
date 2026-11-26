package com.appraisal.repo;

import com.appraisal.domain.Cycle;
import com.appraisal.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CycleRepo extends JpaRepository<Cycle, Long> {
    List<Cycle> findAllByOrderByCreatedAtDesc();
    List<Cycle> findByStatusInOrderByCreatedAtDesc(Collection<Enums.CycleStatus> statuses);
}
