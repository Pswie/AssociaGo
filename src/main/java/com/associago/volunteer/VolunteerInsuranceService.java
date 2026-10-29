package com.associago.volunteer;

import com.associago.volunteer.repository.VolunteerInsuranceRepository;
import com.associago.volunteer.repository.VolunteerPolicyCoverageRepository;
import com.associago.volunteer.repository.VolunteerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * La copertura assicurativa dei volontari — art. 18 D.Lgs. 117/2017.
 *
 * <p>La domanda a cui questo servizio deve saper rispondere non è «esiste una
 * polizza» ma «<em>questa</em> persona era coperta <em>quel</em> giorno». È la
 * prima che viene posta dopo un infortunio, e la sola che conti.
 *
 * <p>Per rispondere servono tre cose insieme: una polizza in corso quel giorno,
 * che comprenda quella persona (cumulativa, oppure nominativa che la nomini), e
 * che copra <strong>entrambe</strong> le garanzie che l'articolo impone —
 * infortuni e responsabilità civile verso terzi. Averne una sola è la situazione
 * più insidiosa, perché sembra copertura.
 */
@Service
@Transactional
public class VolunteerInsuranceService {

    private final VolunteerInsuranceRepository policies;
    private final VolunteerPolicyCoverageRepository coverage;
    private final VolunteerRepository volunteers;

    public VolunteerInsuranceService(VolunteerInsuranceRepository policies,
                                     VolunteerPolicyCoverageRepository coverage,
                                     VolunteerRepository volunteers) {
        this.policies = policies;
        this.coverage = coverage;
        this.volunteers = volunteers;
    }

    /**
     * L'esito di una verifica di copertura.
     *
     * @param infortuni  garanzia infortuni e malattia attiva quel giorno
     * @param rcTerzi    garanzia responsabilità civile verso terzi attiva
     * @param completa   entrambe: è questa che significa «in regola»
     */
    public record Copertura(boolean infortuni, boolean rcTerzi, boolean completa,
                            List<Long> policyIds) {}

    public List<VolunteerInsurancePolicy> findByAssociation(Long associationId) {
        return policies.findByAssociationIdOrderByEndDateDesc(associationId);
    }

    public VolunteerInsurancePolicy save(VolunteerInsurancePolicy policy) {
        if (policy.getStartDate() == null || policy.getEndDate() == null) {
            throw new IllegalArgumentException("Decorrenza e scadenza sono obbligatorie.");
        }
        if (policy.getEndDate().isBefore(policy.getStartDate())) {
            throw new IllegalArgumentException("La scadenza non può precedere la decorrenza.");
        }
        return policies.save(policy);
    }

    /** Nomina una persona in una polizza, rendendola nominativa se non lo era. */
    public void addCovered(Long policyId, Long volunteerId) {
        if (!policies.existsById(policyId)) {
            throw new IllegalArgumentException("Polizza non trovata: " + policyId);
        }
        if (!volunteers.existsById(volunteerId)) {
            throw new IllegalArgumentException("Volontario non trovato: " + volunteerId);
        }
        if (coverage.existsByPolicyIdAndVolunteerId(policyId, volunteerId)) return;

        VolunteerPolicyCoverage riga = new VolunteerPolicyCoverage();
        riga.setPolicyId(policyId);
        riga.setVolunteerId(volunteerId);
        coverage.save(riga);
    }

    public void removeCovered(Long policyId, Long volunteerId) {
        coverage.deleteByPolicyIdAndVolunteerId(policyId, volunteerId);
    }

    /**
     * Se una persona era coperta in un dato giorno, e da quali garanzie.
     *
     * <p>Il giorno è un parametro e non «oggi» di proposito: dopo un infortunio
     * la domanda riguarda sempre una data passata, e un servizio che sappia
     * rispondere solo per il presente non serve proprio quando serve.
     */
    @Transactional(readOnly = true)
    public Copertura coperturaDi(Long associationId, Long volunteerId, LocalDate giorno) {
        LocalDate quando = giorno == null ? LocalDate.now() : giorno;
        List<VolunteerInsurancePolicy> inCorso = policies
                .findByAssociationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        associationId, quando, quando);

        boolean infortuni = false;
        boolean rcTerzi = false;
        List<Long> valide = new java.util.ArrayList<>();

        for (VolunteerInsurancePolicy polizza : inCorso) {
            // Nessuna riga nominativa: la polizza è cumulativa e copre tutti.
            boolean comprende = coverage.countByPolicyId(polizza.getId()) == 0
                    || coverage.existsByPolicyIdAndVolunteerId(polizza.getId(), volunteerId);
            if (!comprende) continue;

            valide.add(polizza.getId());
            String tipo = polizza.getCoverageType();
            if (VolunteerInsurancePolicy.INFORTUNI.equals(tipo)
                    || VolunteerInsurancePolicy.INFORTUNI_RC.equals(tipo)) {
                infortuni = true;
            }
            if (VolunteerInsurancePolicy.RC_TERZI.equals(tipo)
                    || VolunteerInsurancePolicy.INFORTUNI_RC.equals(tipo)) {
                rcTerzi = true;
            }
        }

        return new Copertura(infortuni, rcTerzi, infortuni && rcTerzi, valide);
    }

    /**
     * I volontari attivi che non risultano coperti oggi.
     *
     * <p>È l'elenco che il referente volontari deve vedere prima di un turno, e
     * la ragione per cui questo modulo esiste. Un volontario cessato non
     * compare: non deve operare, quindi non deve essere coperto.
     */
    @Transactional(readOnly = true)
    public List<Volunteer> scoperti(Long associationId, LocalDate giorno) {
        LocalDate quando = giorno == null ? LocalDate.now() : giorno;
        return volunteers.attiviIl(quando).stream()
                .filter(v -> !coperturaDi(associationId, v.getId(), quando).completa())
                .toList();
    }

    /** Le polizze che scadono entro i giorni indicati. */
    @Transactional(readOnly = true)
    public List<VolunteerInsurancePolicy> inScadenza(Long associationId, int giorni) {
        LocalDate oggi = LocalDate.now();
        return policies.findByAssociationIdAndEndDateBetween(associationId, oggi, oggi.plusDays(giorni));
    }
}
