package com.associago.attendance.repository;

import com.associago.attendance.AttendanceRecord;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AttendanceRecordRepository extends CrudRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findBySessionId(Long sessionId);
    List<AttendanceRecord> findByMemberId(Long memberId);
    boolean existsBySessionIdAndMemberIdAndIdNot(Long sessionId, Long memberId, Long excludedId);
    boolean existsBySessionIdAndMembershipIdAndIdNot(Long sessionId, Long membershipId, Long excludedId);
    long deleteBySessionId(Long sessionId);
}
