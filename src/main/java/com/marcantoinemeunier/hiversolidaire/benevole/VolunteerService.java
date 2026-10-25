package com.marcantoinemeunier.hiversolidaire.benevole;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VolunteerService {

    /** Index unique de la migration V7. */
    private static final String PSEUDO_UNIQUE_INDEX = "volunteer_pseudo_unique";

    private final VolunteerRepository volunteers;
    private final Clock clock;

    VolunteerService(VolunteerRepository volunteers, Clock clock) {
        this.volunteers = volunteers;
        this.clock = clock;
    }

    /**
     * Identifiant du bénévole de cette adresse, créé s'il n'existe pas encore (e-mail seul, profil à compléter).
     * Appelé uniquement après la consommation d'un lien magique : le contrôle de l'adresse est prouvé (ADR-0003).
     *
     * @param normalizedEmail adresse déjà normalisée par {@link EmailAddress#normalize(String)}
     */
    @Transactional
    public UUID findOrCreate(String normalizedEmail) {
        volunteers.insertIfAbsent(UUID.randomUUID(), normalizedEmail, clock.instant());
        return volunteers.findIdByEmail(normalizedEmail).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<VolunteerEntity> find(UUID id) {
        return volunteers.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean profileCompleted(UUID id) {
        return volunteers.findById(id).map(VolunteerEntity::profileCompleted).orElse(false);
    }

    /**
     * Enregistre le profil. Le pseudo est vérifié avant l'écriture ; l'index unique {@code lower(pseudo)} tranche si
     * deux bénévoles valident le même pseudo au même instant.
     *
     * @param pseudo pseudo déjà normalisé par {@link Pseudo#normalize(String)}
     * @throws PseudoTakenException si le pseudo est déjà pris
     */
    @Transactional
    public void completeProfile(UUID id, ProfileForm form, String pseudo) {
        if (volunteers.pseudoTaken(pseudo)) {
            throw new PseudoTakenException();
        }
        var volunteer = volunteers.findById(id).orElseThrow();
        volunteer.completeProfile(form.normalizedLastName(), form.normalizedFirstName(), pseudo,
                form.normalizedPhone(), form.isRecontactNextSeason(), clock.instant());
        try {
            volunteers.saveAndFlush(volunteer);
        } catch (DataIntegrityViolationException e) {
            var cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains(PSEUDO_UNIQUE_INDEX)) {
                throw new PseudoTakenException();
            }
            throw e;
        }
    }

    /** Le pseudo proposé s'il est libre, sinon sa première variante libre (« MarieD2 », « MarieD3 »…). */
    @Transactional(readOnly = true)
    public String freePseudo(String pseudo) {
        var candidate = pseudo;
        for (int n = 2; volunteers.pseudoTaken(candidate); n++) {
            candidate = Pseudo.variant(pseudo, n);
        }
        return candidate;
    }

    /** Le pseudo vient d'être pris par un autre bénévole. */
    public static class PseudoTakenException extends RuntimeException {
    }
}
