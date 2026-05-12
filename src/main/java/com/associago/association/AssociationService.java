package com.associago.association;

import com.associago.association.repository.AssociationRepository;
import com.associago.security.dto.AuthRecoveryAssociationDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AssociationService {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private final AssociationRepository associationRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AssociationService(AssociationRepository associationRepository, PasswordEncoder passwordEncoder) {
        this.associationRepository = associationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Association> findAll() {
        return associationRepository.findAll();
    }

    public Optional<Association> findById(Long id) {
        return associationRepository.findById(id);
    }

    public Optional<Association> findBySlug(String slug) {
        return associationRepository.findBySlug(slug);
    }

    public List<AuthRecoveryAssociationDTO> findRecoverableAssociations() {
        return associationRepository.findAll().stream()
                .map(association -> new AuthRecoveryAssociationDTO(
                        association.getId(),
                        association.getName(),
                        association.getEmail(),
                        association.getType()
                ))
                .toList();
    }

    @Transactional
    public Association create(Association association) {
        if (associationRepository.findBySlug(association.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Slug already exists: " + association.getSlug());
        }
        // Hash the password before saving
        if (association.getPassword() != null && !association.getPassword().isEmpty()) {
            association.setPassword(encodePasswordIfNeeded(association.getPassword()));
        }
        return associationRepository.save(association);
    }

    public boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    @Transactional
    public Association resetPasswordWithFiscalCode(Long associationId, String fiscalCode, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        return associationRepository.findById(associationId)
                .map(association -> {
                    if (!sameNormalizedCode(fiscalCode, association.getTaxCode())) {
                        throw new IllegalArgumentException("Fiscal code does not match");
                    }
                    association.setPassword(passwordEncoder.encode(newPassword));
                    return associationRepository.save(association);
                })
                .orElseThrow(() -> new IllegalArgumentException("Association not found"));
    }

    @Transactional
    public void upgradePasswordHash(Long id, String rawPassword) {
        associationRepository.findById(id).ifPresent(association -> {
            if (!isBcryptHash(association.getPassword()) && passwordMatches(rawPassword, association.getPassword())) {
                association.setPassword(passwordEncoder.encode(rawPassword));
                associationRepository.save(association);
            }
        });
    }

    @Transactional
    public Association update(Long id, Association updatedAssociation) {
        return associationRepository.findById(id)
                .map(existing -> {
                    // Update fields
                    existing.setName(updatedAssociation.getName());
                    existing.setEmail(updatedAssociation.getEmail());
                    existing.setTaxCode(updatedAssociation.getTaxCode());
                    existing.setVatNumber(updatedAssociation.getVatNumber());
                    existing.setType(updatedAssociation.getType());
                    existing.setAddress(updatedAssociation.getAddress());
                    existing.setCity(updatedAssociation.getCity());
                    existing.setProvince(updatedAssociation.getProvince());
                    existing.setZipCode(updatedAssociation.getZipCode());
                    existing.setPhone(updatedAssociation.getPhone());
                    existing.setDescription(updatedAssociation.getDescription());
                    
                    existing.setPresident(updatedAssociation.getPresident());
                    existing.setVicePresident(updatedAssociation.getVicePresident());
                    existing.setSecretary(updatedAssociation.getSecretary());
                    existing.setTreasurer(updatedAssociation.getTreasurer());
                    
                    existing.setFoundationDate(updatedAssociation.getFoundationDate());
                    existing.setMembershipNumberFormat(updatedAssociation.getMembershipNumberFormat());
                    existing.setBaseMembershipFee(updatedAssociation.getBaseMembershipFee());
                    
                    // DB Config
                    existing.setUseRemoteDb(updatedAssociation.isUseRemoteDb());
                    existing.setDbType(updatedAssociation.getDbType());
                    existing.setDbHost(updatedAssociation.getDbHost());
                    existing.setDbPort(updatedAssociation.getDbPort());
                    existing.setDbName(updatedAssociation.getDbName());
                    existing.setDbUser(updatedAssociation.getDbUser());
                    existing.setDbPassword(updatedAssociation.getDbPassword());
                    existing.setDbSsl(updatedAssociation.isDbSsl());

                    // Logo (if provided)
                    if (updatedAssociation.getLogo() != null) {
                        existing.setLogo(updatedAssociation.getLogo());
                    }
                    
                    return associationRepository.save(existing);
                })
                .orElseThrow(() -> new IllegalArgumentException("Association not found with id: " + id));
    }

    @Transactional
    public void updateLogo(Long id, byte[] logoData) {
        associationRepository.findById(id).ifPresent(assoc -> {
            assoc.setLogo(logoData);
            associationRepository.save(assoc);
        });
    }

    @Transactional(readOnly = true)
    public byte[] getLogo(Long id) {
        return associationRepository.findById(id)
                .map(Association::getLogo)
                .orElse(null);
    }

    @Transactional
    public void delete(Long id) {
        associationRepository.deleteById(id);
    }

    private String encodePasswordIfNeeded(String password) {
        if (isBcryptHash(password)) {
            return password;
        }
        return passwordEncoder.encode(password);
    }

    private boolean isBcryptHash(String password) {
        return password != null && BCRYPT_PATTERN.matcher(password).matches();
    }

    private boolean sameNormalizedCode(String submittedCode, String storedCode) {
        String submitted = normalizeCode(submittedCode);
        String stored = normalizeCode(storedCode);
        return !submitted.isEmpty() && submitted.equals(stored);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toUpperCase();
    }
}
