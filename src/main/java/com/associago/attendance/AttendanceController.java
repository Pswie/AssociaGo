package com.associago.attendance;

import com.associago.attendance.dto.*;
import com.associago.attendance.mapper.AttendanceMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/attendance", "/api/v1/attendance"})
public class AttendanceController {
    private final AttendanceService service;
    public AttendanceController(AttendanceService service) { this.service = service; }

    @GetMapping("/activity/{activityId}")
    public List<AttendanceSessionDTO> sessions(@PathVariable Long activityId, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return service.getSessionsByActivity(activityId, actor, association).stream().map(AttendanceMapper::toDTO).toList();
    }
    @GetMapping("/activity/{activityId}/participants")
    public List<AttendanceParticipantDTO> participants(@PathVariable Long activityId, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return service.participants(activityId, actor, association);
    }
    @GetMapping("/session/{id}")
    public AttendanceSessionDTO session(@PathVariable Long id, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return AttendanceMapper.toDTO(service.getSessionById(id, actor, association));
    }
    @PostMapping("/session")
    public AttendanceSessionDTO createSession(@RequestBody AttendanceSessionUpsertDTO dto, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return AttendanceMapper.toDTO(service.createSession(dto, actor, association));
    }
    @PutMapping("/session/{id}")
    public AttendanceSessionDTO updateSession(@PathVariable Long id, @RequestBody AttendanceSessionUpsertDTO dto, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return AttendanceMapper.toDTO(service.updateSession(id, dto, actor, association));
    }
    @DeleteMapping("/session/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        service.deleteSession(id, actor, association);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/session/{id}/records")
    public List<AttendanceRecordDTO> records(@PathVariable Long id, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return service.getRecordsBySession(id, actor, association).stream().map(AttendanceMapper::toDTO).toList();
    }
    @PostMapping("/record")
    public AttendanceRecordDTO createRecord(@RequestBody AttendanceRecordUpsertDTO dto, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return AttendanceMapper.toDTO(service.createRecord(dto, actor, association));
    }
    @PutMapping("/record/{id}")
    public AttendanceRecordDTO updateRecord(@PathVariable Long id, @RequestBody AttendanceRecordUpsertDTO dto, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        return AttendanceMapper.toDTO(service.updateRecord(id, dto, actor, association));
    }
    @DeleteMapping("/record/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id, Authentication actor,
            @RequestHeader(value = "X-Association-Id", required = false) Long association) {
        service.deleteRecord(id, actor, association);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("code", "ATTENDANCE_CONFLICT", "message",
                "La presenza è già registrata oppure il partecipante non è più disponibile. Ricaricare e riprovare."));
    }
}
