package com.associago.membership;

import com.associago.association.mapper.AssociationMapper;
import com.associago.association.repository.AssociationRepository;
import com.associago.suite.SuiteException;
import com.associago.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {
    private final MembershipService service;
    private final MembershipAccess access;
    private final UserRepository users;
    private final AssociationRepository associations;
    public MembershipController(MembershipService service, MembershipAccess access, UserRepository users, AssociationRepository associations) {
        this.service = service; this.access = access; this.users = users; this.associations = associations;
    }
    @GetMapping("/association/{associationId}")
    public List<MembershipDTO> list(@PathVariable Long associationId, @RequestParam(required = false) Integer anno,
            Authentication actor, @RequestHeader(value="X-Association-Id",required=false) Long selected) {
        access.require(actor, associationId, selected);
        return service.findNellEsercizio(associationId, anno).stream().map(MembershipController::view).toList();
    }
    @GetMapping("/{id}")
    public MembershipDTO get(@PathVariable Long id, Authentication actor,
            @RequestHeader(value="X-Association-Id",required=false) Long selected) {
        return view(authorized(id, actor, selected));
    }
    @PostMapping
    @Transactional
    public MembershipDTO create(@RequestBody MembershipUpsertDTO dto, Authentication actor,
            @RequestHeader(value="X-Association-Id",required=false) Long selected) {
        Long associationId = dto.association() == null ? null : dto.association().id();
        // This authorization MUST precede any save: the requested new membership
        // can never be used to authorize its creator in a different association.
        access.require(actor, associationId, selected);
        access.createId(dto.id());
        Long userId = dto.user() == null ? null : dto.user().id();
        if (associationId == null || associationId <= 0 || userId == null || userId <= 0) {
            throw new SuiteException(400, "MEMBERSHIP_REFERENCE_REQUIRED", "Indicare persona e associazione con identificativi validi.");
        }
        access.requireNew(userId, associationId);
        var entity = new UserAssociation();
        entity.setId(dto.id());
        entity.setUser(users.findById(userId).orElseThrow(() -> missing("Persona non trovata.")));
        entity.setAssociation(associations.findById(associationId).orElseThrow(() -> missing("Associazione non trovata.")));
        updateFields(entity, dto);
        return view(service.save(entity));
    }
    @PutMapping("/{id}")
    @Transactional
    public MembershipDTO update(@PathVariable Long id, @RequestBody MembershipUpsertDTO dto, Authentication actor,
            @RequestHeader(value="X-Association-Id",required=false) Long selected) {
        var existing = authorized(id, actor, selected);
        access.immutable(dto.id(), id);
        access.immutable(dto.user() == null ? null : dto.user().id(), existing.getUser() == null ? null : existing.getUser().getId());
        access.immutable(dto.association() == null ? null : dto.association().id(), existing.getAssociation() == null ? null : existing.getAssociation().getId());
        updateFields(existing, dto);
        return view(service.save(existing));
    }
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication actor,
            @RequestHeader(value="X-Association-Id",required=false) Long selected) {
        authorized(id, actor, selected);
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("code", "MEMBERSHIP_CONFLICT", "message",
                "L'iscrizione è già presente oppure è collegata a dati storici. Ricaricare e verificare."));
    }
    private UserAssociation authorized(Long id, Authentication actor, Long selected) {
        var entity = service.findById(id).orElseThrow(() -> missing("Iscrizione non trovata."));
        access.require(actor, entity.getAssociation() == null ? null : entity.getAssociation().getId(), selected);
        return entity;
    }
    private static void updateFields(UserAssociation entity, MembershipUpsertDTO dto) {
        if (dto.role() != null) entity.setRole(dto.role());
        if (dto.status() != null) entity.setStatus(dto.status());
        if (dto.joinDate() != null) entity.setJoinDate(dto.joinDate());
        entity.setMembershipCardNumber(dto.membershipCardNumber());
        entity.setExpirationDate(dto.expirationDate()); entity.setLastRenewalDate(dto.lastRenewalDate());
        entity.setNotes(dto.notes());
        if (dto.boardMember() != null) entity.setBoardMember(dto.boardMember());
    }
    private static MembershipDTO view(UserAssociation entity) {
        var user = entity.getUser();
        var person = user == null ? null : new MembershipUserDTO(user.getId(), user.getFirstName(), user.getLastName(), user.getTaxCode(),
                user.getEmail(), user.getPhone(), user.getBirthDate(), user.getBirthPlace(), user.getGender(), user.getAddress(),
                user.getCity(), user.getZipCode(), user.getCreatedAt(), user.getUpdatedAt());
        return new MembershipDTO(entity.getId(), person, entity.getAssociation() == null ? null : AssociationMapper.toDTO(entity.getAssociation()),
                entity.getMembershipCardNumber(), entity.getRole(), entity.getStatus(), entity.getJoinDate(), entity.getExpirationDate(),
                entity.getLastRenewalDate(), entity.getNotes(), entity.isBoardMember(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private static SuiteException missing(String message) { return new SuiteException(404,"MEMBERSHIP_NOT_FOUND",message); }
}
