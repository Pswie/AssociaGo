package com.associago.attendance.repository;

import com.associago.attendance.AttendanceSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AttendanceSessionRepository extends CrudRepository<AttendanceSession, Long> {
    List<AttendanceSession> findByActivityId(Long activityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM AttendanceSession session WHERE session.id = :id")
    Optional<AttendanceSession> findForUpdate(@Param("id") Long id);
}
