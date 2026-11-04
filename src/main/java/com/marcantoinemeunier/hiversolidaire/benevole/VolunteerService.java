package com.marcantoinemeunier.hiversolidaire.benevole;

import java.time.Clock;
import java.util.List;
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

    /** Nouveaux bénévoles à signaler aux coordinateurs (§2), les plus anciens d'abord. */
    @Transactional(readOnly = true)
    public List<VolunteerContact> toReview() {
        return volunteers.findToReview().stream().map(VolunteerEntity::toContact).toList();
    }

    /**
     * Un coordinateur a vu ce bénévole : il n'est plus signalé comme nouveau.
     *
     * @return faux si le bénévole n'existe pas ou que son compte est supprimé
     */
    @Transactional
    public boolean markReviewed(UUID id) {
        var volunteer = volunteers.findById(id).filter(v -> !v.deleted());
        volunteer.ifPresent(v -> v.markReviewed(clock.instant()));
        return volunteer.isPresent();
    }

    /** Coordonnées d'un bénévole actif (back-office), vide s'il n'existe pas ou que son compte est supprimé. */
    @Transactional(readOnly = true)
    public Optional<VolunteerContact> contact(UUID id) {
        return volunteers.findById(id).filter(v -> !v.deleted()).map(VolunteerEntity::toContact);
    }

    /**
     * Recherche du back-office sur le nom, le prénom, le pseudo, l'e-mail et le téléphone, sans tenir compte de la
     * casse. Les caractères spéciaux de {@code LIKE} sont échappés : « % » cherche un « % ». Vide : tous les comptes
     * actifs.
     */
    @Transactional(readOnly = true)
    public List<VolunteerContact> search(String query) {
        var text = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        if (text.length() > 100) {
            text = text.substring(0, 100);
        }
        var escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return volunteers.search("%" + escaped + "%").stream().map(VolunteerEntity::toContact).toList();
    }

    /** Bénévoles actifs au profil rempli, par nom : ceux qu'un coordinateur peut inscrire. */
    @Transactional(readOnly = true)
    public List<VolunteerContact> activeWithProfile() {
        return volunteers.findActiveWithProfile().stream().map(VolunteerEntity::toContact).toList();
    }

    /**
     * Bénévole inscrit par téléphone par un coordinateur, sans e-mail (§6 « Fiche soirée ») : mêmes règles de nom, de
     * prénom, de pseudo et de téléphone qu'au premier profil. Il ne peut pas se connecter.
     *
     * @param pseudo pseudo déjà normalisé par {@link Pseudo#normalize(String)}
     * @throws PseudoTakenException si le pseudo est déjà pris
     */
    @Transactional
    public UUID createWithoutEmail(ProfileForm form, String pseudo) {
        if (volunteers.pseudoTaken(pseudo)) {
            throw new PseudoTakenException();
        }
        var volunteer = VolunteerEntity.withoutEmail(form.normalizedLastName(), form.normalizedFirstName(), pseudo,
                form.normalizedPhone(), clock.instant());
        saveCheckingPseudo(volunteer);
        return volunteer.id();
    }

    /**
     * Correction du nom, du prénom et du pseudo par un coordinateur (pseudo usurpé, §11 question 8) : mêmes règles et
     * même unicité que pour le bénévole.
     *
     * @param pseudo pseudo déjà normalisé par {@link Pseudo#normalize(String)}
     * @throws PseudoTakenException si un autre bénévole a déjà ce pseudo
     */
    @Transactional
    public void correctIdentity(UUID id, IdentityForm form, String pseudo) {
        if (volunteers.pseudoTakenByOther(pseudo, id)) {
            throw new PseudoTakenException();
        }
        var volunteer = volunteers.findById(id).filter(v -> !v.deleted()).orElseThrow();
        volunteer.correctIdentity(form.normalizedLastName(), form.normalizedFirstName(), pseudo, clock.instant());
        saveCheckingPseudo(volunteer);
    }

    /** Le pseudo vient d'être pris par un autre bénévole. */
    public static class PseudoTakenException extends RuntimeException {
    }
}
