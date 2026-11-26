package com.appraisal.repo;

import com.appraisal.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepo extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<AppUser> findAllByOrderByFullNameAsc();
    List<AppUser> findByActiveTrueAndAppraisedTrueAndTeamIsNotNull();
    long countByLevelId(Long levelId);
    long countByTeamId(Long teamId);
}
