package com.associago.association;

import com.associago.association.dto.AssociationCreateDTO;
import com.associago.association.dto.AssociationDTO;
import com.associago.association.dto.AssociationUpdateDTO;
import com.associago.association.mapper.AssociationMapper;
import com.associago.access.SuiteAccessService;
import com.associago.suite.SuiteException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping({"/api/associations", "/api/v1/associations"})
public class AssociationController {

    private final AssociationService associationService;
    @org.springframework.beans.factory.annotation.Value("${SUITE_ACCESS_ENABLED:false}")
    private boolean suiteAccessEnabled;
    @org.springframework.beans.factory.annotation.Value("${SUITE_ASSOCIATION_ID:0}")
    private Long suiteAssociationId;
    @org.springframework.beans.factory.annotation.Value("${SUITE_ACCESS_OWNER_SUBJECT:}")
    private String suiteOwnerSubject;

    @Autowired
    public AssociationController(AssociationService associationService) {
        this.associationService = associationService;
    }

    @GetMapping
    public List<AssociationDTO> getAllAssociations() {
        if (suiteAccessEnabled) {
            return associationService.findById(configuredAssociation()).stream().map(AssociationMapper::toDTO).toList();
        }
        return associationService.findAll().stream()
                .map(AssociationMapper::toDTO)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssociationDTO> getAssociationById(@PathVariable Long id, Authentication actor) {
        require(id, actor, false);
        return associationService.findById(id)
                .map(AssociationMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public AssociationDTO createAssociation(@Valid @RequestBody AssociationCreateDTO dto) {
        requireCreation();
        Association entity = AssociationMapper.toEntity(dto);
        return AssociationMapper.toDTO(associationService.create(entity));
    }

    @PostMapping("/setup")
    public AssociationDTO setupAssociation(@Valid @RequestBody AssociationCreateDTO dto) {
        requireCreation();
        Association entity = AssociationMapper.toEntity(dto);
        return AssociationMapper.toDTO(associationService.create(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssociationDTO> updateAssociation(@PathVariable Long id,
                                                            @Valid @RequestBody AssociationUpdateDTO dto,
                                                            Authentication actor) {
        require(id, actor, true);
        if (suiteAccessEnabled && (dto.useRemoteDb() != null || dto.dbType() != null || dto.dbHost() != null ||
                dto.dbPort() != null || dto.dbName() != null || dto.dbUser() != null || dto.dbPassword() != null || dto.dbSsl() != null))
            requireOwner(actor);
        return associationService.findById(id)
                .map(existing -> {
                    AssociationMapper.updateEntity(existing, dto);
                    Association updated = associationService.update(id, existing);
                    return ResponseEntity.ok(AssociationMapper.toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssociation(@PathVariable Long id, Authentication actor) {
        require(id, actor, true);
        if (suiteAccessEnabled) requireOwner(actor);
        associationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Logo Management ---

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                            Authentication actor) {
        require(id, actor, true);
        try {
            associationService.updateLogo(id, file.getBytes());
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable Long id, Authentication actor) {
        require(id, actor, false);
        byte[] logo = associationService.getLogo(id);
        if (logo == null || logo.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE) // Default to PNG, browser handles detection usually
                .body(logo);
    }

    private Long configuredAssociation() {
        if (suiteAssociationId == null || suiteAssociationId <= 0)
            throw new SuiteException(503, "ASSOCIATION_CONFIGURATION", "Associazione non configurata.");
        return suiteAssociationId;
    }

    private void require(Long id, Authentication actor, boolean write) {
        if (!suiteAccessEnabled) return;
        if (!configuredAssociation().equals(id))
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Associazione non autorizzata.");
        if (!(actor instanceof JwtAuthenticationToken) ||
                !SuiteAccessService.permits(actor, "associago.settings." + (write ? "write" : "read")))
            throw new SuiteException(403, "MODULE_FORBIDDEN", "Accesso non autorizzato a questa sezione.");
    }

    private void requireCreation() {
        if (suiteAccessEnabled)
            throw new SuiteException(403, "ASSOCIATION_FIXED", "Questa installazione gestisce una sola associazione.");
    }

    private void requireOwner(Authentication actor) {
        if (!(actor instanceof JwtAuthenticationToken jwt) || !actor.isAuthenticated() ||
                !jwt.getToken().getSubject().equals(suiteOwnerSubject))
            throw new SuiteException(403, "OWNER_REQUIRED", "Operazione riservata al titolare.");
    }
}
