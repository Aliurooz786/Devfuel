package com.devfuel.log;

import com.devfuel.common.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventLogRepository extends JpaRepository<EventLog, UUID> {

    List<EventLog> findAllByOrderByEventTimestampDescLoggedAtDesc();

    @Query("""
            SELECT e FROM EventLog e
            WHERE LOWER(e.rawText) LIKE LOWER(CONCAT('%', :q, '%'))
              AND (:eventType IS NULL OR e.eventType = :eventType)
            ORDER BY e.eventTimestamp DESC, e.loggedAt DESC
            """)
    List<EventLog> search(@Param("q") String q, @Param("eventType") EventType eventType);
}
