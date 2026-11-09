package com.marcantoinemeunier.hiversolidaire.planning.season;

import com.marcantoinemeunier.hiversolidaire.planning.domain.Day;
import com.marcantoinemeunier.hiversolidaire.planning.domain.Post;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Création ou modification d'une saison par un coordinateur. Validé côté serveur ; les textes (horaires, contact)
 * sont bornés et toujours affichés avec {@code th:text}. Le contact est vérifié par {@code CoordinatorsContact}.
 */
public class SeasonForm {

    /** Une saison dure au plus ce nombre de soirées (environ 76 en 2025-2026). */
    static final int MAX_EVENINGS = 200;

    @NotNull(message = "{admin.season.error.dates}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate firstEvening;

    @NotNull(message = "{admin.season.error.dates}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lastEvening;

    @NotNull(message = "{admin.season.error.opensAt}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME, fallbackPatterns = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime registrationsOpenAt;

    @Valid
    private List<NeedForm> needs = new ArrayList<>();

    @Size(max = 254, message = "{admin.season.error.contact}")
    private String coordinatorsContact;

    /** Organisateur affiché sur la page d'accueil : une ligne, 100 caractères au plus (colonne {@code organizer}). */
    @Size(max = ORGANIZER_MAX_LENGTH, message = "{admin.season.error.organizer}")
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "{admin.season.error.organizer}")
    private String organizer;

    static final int ORGANIZER_MAX_LENGTH = 100;

    /** Formulaire vide d'une nouvelle saison : besoins de 2025-2026 (§5). */
    public static SeasonForm blank() {
        var form = new SeasonForm();
        form.needs = new ArrayList<>(List.of(
                NeedForm.of(Post.BREAKFAST, 1, 1, null),
                NeedForm.of(Post.MAIN_DISH, 1, 1, null),
                NeedForm.of(Post.SIDES, 1, 1, null),
                NeedForm.of(Post.DINNER_PRESENCE, 2, 2, null),
                NeedForm.of(Post.NIGHT, 1, 2, null)));
        return form;
    }

    public static SeasonForm of(SeasonSettings settings) {
        var form = new SeasonForm();
        form.firstEvening = settings.firstEvening();
        form.lastEvening = settings.lastEvening();
        form.registrationsOpenAt = settings.registrationsOpenAt();
        form.needs = new ArrayList<>(Day.POSTS.stream()
                .map(post -> settings.needs().stream().filter(need -> need.post() == post).findFirst()
                        .map(need -> NeedForm.of(post, need.minimum(), need.maximum(), need.schedule()))
                        .orElseThrow())
                .toList());
        form.coordinatorsContact = settings.coordinatorsContact();
        form.organizer = settings.organizer();
        return form;
    }

    @AssertTrue(message = "{admin.season.error.dates}")
    public boolean isDatesValid() {
        if (firstEvening == null || lastEvening == null) {
            return true;
        }
        return !lastEvening.isBefore(firstEvening)
                && firstEvening.datesUntil(lastEvening.plusDays(1)).count() <= MAX_EVENINGS;
    }

    /** Un besoin par poste, chacun une seule fois : le formulaire ne choisit pas les postes. */
    @AssertTrue(message = "{admin.season.error.needs}")
    public boolean isNeedsComplete() {
        return needs != null && needs.size() == Day.POSTS.size()
                && needs.stream().map(NeedForm::getPost).distinct().count() == Day.POSTS.size()
                && needs.stream().allMatch(need -> need.getPost() != null);
    }

    public static class NeedForm {

        private Post post;

        @Min(value = 0, message = "{admin.season.error.need}")
        @Max(value = 10, message = "{admin.season.error.need}")
        private int minimum;

        @Min(value = 0, message = "{admin.season.error.need}")
        @Max(value = 10, message = "{admin.season.error.need}")
        private int maximum;

        @Size(max = 100, message = "{admin.season.error.schedule}")
        private String schedule;

        static NeedForm of(Post post, int minimum, int maximum, String schedule) {
            var form = new NeedForm();
            form.post = post;
            form.minimum = minimum;
            form.maximum = maximum;
            form.schedule = schedule;
            return form;
        }

        @AssertTrue(message = "{admin.season.error.need}")
        public boolean isBoundsValid() {
            return maximum >= minimum && maximum >= 1;
        }

        public Post getPost() {
            return post;
        }

        public void setPost(Post post) {
            this.post = post;
        }

        public int getMinimum() {
            return minimum;
        }

        public void setMinimum(int minimum) {
            this.minimum = minimum;
        }

        public int getMaximum() {
            return maximum;
        }

        public void setMaximum(int maximum) {
            this.maximum = maximum;
        }

        public String getSchedule() {
            return schedule;
        }

        public void setSchedule(String schedule) {
            this.schedule = schedule;
        }
    }

    public LocalDate getFirstEvening() {
        return firstEvening;
    }

    public void setFirstEvening(LocalDate firstEvening) {
        this.firstEvening = firstEvening;
    }

    public LocalDate getLastEvening() {
        return lastEvening;
    }

    public void setLastEvening(LocalDate lastEvening) {
        this.lastEvening = lastEvening;
    }

    public LocalDateTime getRegistrationsOpenAt() {
        return registrationsOpenAt;
    }

    public void setRegistrationsOpenAt(LocalDateTime registrationsOpenAt) {
        this.registrationsOpenAt = registrationsOpenAt;
    }

    public List<NeedForm> getNeeds() {
        return needs;
    }

    public void setNeeds(List<NeedForm> needs) {
        this.needs = needs;
    }

    public String getCoordinatorsContact() {
        return coordinatorsContact;
    }

    public void setCoordinatorsContact(String coordinatorsContact) {
        this.coordinatorsContact = coordinatorsContact;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    /** Texte facultatif : espaces retirés autour, vide devient {@code null}. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
