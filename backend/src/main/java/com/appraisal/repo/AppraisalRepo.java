package com.appraisal.repo;

import com.appraisal.domain.Appraisal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppraisalRepo extends JpaRepository<Appraisal, Long> {
    List<Appraisal> findByCycleIdOrderByTeamNameAscEmployeeFullNameAsc(Long cycleId);
    Optional<Appraisal> findByCycleIdAndEmployeeId(Long cycleId, Long employeeId);
    long countByCycleId(Long cycleId);
}
