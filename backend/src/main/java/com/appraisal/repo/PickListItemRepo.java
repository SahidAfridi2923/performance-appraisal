package com.appraisal.repo;

import com.appraisal.domain.Enums;
import com.appraisal.domain.PickListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PickListItemRepo extends JpaRepository<PickListItem, Long> {
    List<PickListItem> findByListTypeOrderBySortOrderAsc(Enums.PickListType type);
    void deleteByListType(Enums.PickListType type);
}
