package com.associago.cloud;

import com.associago.activity.Activity;
import com.associago.association.Association;
import com.associago.attendance.AttendanceRecord;
import com.associago.attendance.AttendanceSession;
import com.associago.member.Member;
import com.associago.membership.UserAssociation;
import com.associago.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

final class AttendanceMappingProbe {
    static void verify(EntityManager entityManager) {
        var association = new Association(); association.setName("Attendance mapping fixture"); association.setSlug("attendance-" + java.util.UUID.randomUUID()); association.setEmail("attendance-fixture@example.invalid"); entityManager.persist(association);
        var user = new User(); user.setFirstName("Membership"); user.setLastName("Fixture"); entityManager.persist(user);
        var membership = new UserAssociation(); membership.setUser(user); membership.setAssociation(association); entityManager.persist(membership);
        var member = new Member(); member.setFirstName("Historical"); member.setLastName("Fixture"); entityManager.persist(member);
        var activity = new Activity(); activity.setName("Attendance fixture"); activity.setAssociationId(association.getId()); entityManager.persist(activity);
        var session = new AttendanceSession(); session.setActivityId(activity.getId()); session.setDate(LocalDate.of(2026, 10, 30)); entityManager.persist(session);
        var record = new AttendanceRecord(); record.setSessionId(session.getId()); record.setMembershipId(membership.getId()); record.setStatus("PRESENT"); record.setNotes("explicit membership"); entityManager.persist(record);
        for (int index = 0; index < 2; index++) {
            var historical = new AttendanceRecord(); historical.setSessionId(session.getId()); historical.setMemberId(member.getId()); historical.setStatus("PRESENT"); entityManager.persist(historical);
        }
        entityManager.flush(); entityManager.clear();
        var loaded = entityManager.find(AttendanceRecord.class, record.getId());
        assertThat(loaded.getMembershipId()).isEqualTo(membership.getId()); assertThat(loaded.getMemberId()).isNull(); assertThat(loaded.getNotes()).isEqualTo("explicit membership");
        var duplicate = new AttendanceRecord(); duplicate.setSessionId(session.getId()); duplicate.setMembershipId(membership.getId());
        assertThatThrownBy(() -> { entityManager.persist(duplicate); entityManager.flush(); }).isInstanceOf(PersistenceException.class);
    }
}
