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
        saveCheckingPseudo(volunteer);
    }

    /** Données du bénévole pour « Mon compte ». */
    @Transactional(readOnly = true)
    public Account account(UUID id) {
        return volunteers.findById(id).orElseThrow().toAccount();
    }

    /** Nouveau téléphone, déjà validé ({@link PhoneFields}). */
    @Transactional
    public void changePhone(UUID id, PhoneFields form) {
        volunteers.findById(id).orElseThrow().changePhone(form.normalizedPhone(), clock.instant());
    }

    /**
     * Nouveau pseudo : mêmes règles et même unicité qu'au premier profil. Le bénévole peut garder le sien ou en
     * changer la casse.
     *
     * @param pseudo pseudo déjà normalisé par {@link Pseudo#normalize(String)}
     * @throws PseudoTakenException si un autre bénévole a déjà ce pseudo
     */
    @Transactional
    public void changePseudo(UUID id, String pseudo) {
        if (volunteers.pseudoTakenByOther(pseudo, id)) {
            throw new PseudoTakenException();
        }
        var volunteer = volunteers.findById(id).orElseThrow();
        volunteer.changePseudo(pseudo, clock.instant());
        saveCheckingPseudo(volunteer);
    }

    /** « Me recontacter la saison prochaine » : accord donné ou retiré (§7, consentement retirable). */
    @Transactional
    public void changeRecontact(UUID id, boolean recontactNextSeason) {
        volunteers.findById(id).orElseThrow().changeRecontact(recontactNextSeason, clock.instant());
    }

    /** L'index unique {@code lower(pseudo)} tranche si deux bénévoles valident le même pseudo au même instant. */
    private void saveCheckingPseudo(VolunteerEntity volunteer) {
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
