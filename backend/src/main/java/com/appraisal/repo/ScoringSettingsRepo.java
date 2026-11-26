package com.appraisal.repo;

import com.appraisal.domain.ScoringSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringSettingsRepo extends JpaRepository<ScoringSettings, Long> {
}
