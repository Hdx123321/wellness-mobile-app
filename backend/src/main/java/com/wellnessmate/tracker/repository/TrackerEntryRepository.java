package com.wellnessmate.tracker.repository;

import com.wellnessmate.tracker.domain.TrackerEntry;
import com.wellnessmate.tracker.domain.TrackerType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tracker entry persistence constrained by authenticated ownership. @author TODO(team member) */
public interface TrackerEntryRepository extends JpaRepository<TrackerEntry, Long> {
  Optional<TrackerEntry> findByIdAndUserId(Long id, Long userId);

  @Query("""
      select entry from TrackerEntry entry
      where entry.userId = :userId
        and (:type is null or entry.trackerType = :type)
        and (:fromTime is null or entry.recordedAt >= :fromTime)
        and (:toTime is null or entry.recordedAt < :toTime)
      """)
  Page<TrackerEntry> findOwned(
      @Param("userId") Long userId,
      @Param("type") TrackerType type,
      @Param("fromTime") Instant fromTime,
      @Param("toTime") Instant toTime,
      Pageable pageable);
}
