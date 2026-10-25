package com.marcantoinemeunier.hiversolidaire.benevole;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VolunteerService {

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

    @Transactional
    public void completeProfile(UUID id, ProfileForm form) {
        var volunteer = volunteers.findById(id).orElseThrow();
        volunteer.completeProfile(form.normalizedLastName(), form.normalizedFirstName(), form.normalizedPhone(),
                form.isRecontactNextSeason(), clock.instant());
    }
}
