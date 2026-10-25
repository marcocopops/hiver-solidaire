package com.marcantoinemeunier.hiversolidaire.benevole;

import com.marcantoinemeunier.hiversolidaire.inscription.RegistrationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppression du compte par le bénévole (§6 « Mon compte », ADR-0008), en une transaction :
 * <ol>
 *   <li>ses inscriptions à venir sont annulées, sous le verrou de chaque soirée, avec l'alerte éventuelle ;</li>
 *   <li>sa ligne est anonymisée : ses inscriptions passées restent comptées, rattachées à personne ;</li>
 *   <li>ses liens de connexion en cours sont supprimés (ils contiennent son adresse) ;</li>
 *   <li>toutes ses sessions sont supprimées (ADR-0007), sur tous ses appareils.</li>
 * </ol>
 * Le contrôleur invalide ensuite la session de la requête en cours, pour qu'elle ne soit pas réécrite.
 */
@Service
public class AccountDeletionService {

    private final VolunteerRepository volunteers;
    private final RegistrationService registrations;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AccountDeletionService(VolunteerRepository volunteers, RegistrationService registrations, JdbcTemplate jdbc,
            Clock clock) {
        this.volunteers = volunteers;
        this.registrations = registrations;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void delete(UUID volunteerId) {
        registrations.cancelAllUpcoming(volunteerId);
        var volunteer = volunteers.findById(volunteerId).orElseThrow();
        var email = volunteer.email();
        volunteer.anonymize(clock.instant());
        volunteers.saveAndFlush(volunteer);
        if (email != null) {
            jdbc.update("DELETE FROM one_time_token WHERE username = ?", email);
        }
        // Les attributs suivent par ON DELETE CASCADE (V6).
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", volunteerId.toString());
    }
}
