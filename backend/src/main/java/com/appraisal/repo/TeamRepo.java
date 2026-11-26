package com.appraisal.repo;

import com.appraisal.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepo extends JpaRepository<Team, Long> {
    List<Team> findAllByOrderBySortOrderAscNameAsc();
    Optional<Team> findByCode(String code);
}
