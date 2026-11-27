package com.appraisal.repo;

import com.appraisal.domain.CycleMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CycleMembershipRepo extends JpaRepository<CycleMembership, Long> {
    List<CycleMembership> findByCycleId(Long cycleId);
    Optional<CycleMembership> findByCycleIdAndUserId(Long cycleId, Long userId);
}
