package com.appraisal.repo;

import com.appraisal.domain.ScaleLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScaleLabelRepo extends JpaRepository<ScaleLabel, Long> {
    List<ScaleLabel> findAllByOrderByValueDesc();
}
