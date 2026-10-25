package com.marcantoinemeunier.hiversolidaire.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Component;

/**
 * Stockage des jetons du lien magique, branché sur le One-Time Token Login de Spring Security (ADR-0003).
 * Remplace {@code JdbcOneTimeTokenService} pour deux raisons :
 * <ul>
 *   <li>seule l'empreinte SHA-256 du jeton est en base : lire la table ne donne aucun lien utilisable ;</li>
 *   <li>la consommation est un seul {@code DELETE … RETURNING} : atomique, donc à usage unique même si deux
 *       requêtes présentent le même jeton en même temps ({@code JdbcOneTimeTokenService} fait un
 *       {@code SELECT} puis un {@code DELETE}).</li>
 * </ul>
 * Le jeton, l'empreinte et l'adresse ne sont jamais journalisés.
 */
@Component
public class HashedJdbcOneTimeTokenService implements OneTimeTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 64;

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final Clock clock;

    HashedJdbcOneTimeTokenService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiresAt = clock.instant().plus(request.getExpiresIn());
        jdbc.update("INSERT INTO one_time_token (token_hash, username, expires_at) VALUES (?, ?, ?)",
                hash(value), request.getUsername(), Timestamp.from(expiresAt));
        return new DefaultOneTimeToken(value, request.getUsername(), expiresAt);
    }

    @Override
    public OneTimeToken consume(OneTimeTokenAuthenticationToken authenticationToken) {
        var value = authenticationToken.getTokenValue();
        if (value == null || value.isEmpty() || value.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        var consumed = jdbc.query(
                "DELETE FROM one_time_token WHERE token_hash = ? RETURNING username, expires_at",
                (row, index) -> new DefaultOneTimeToken(value, row.getString("username"),
                        row.getTimestamp("expires_at").toInstant()),
                hash(value));
        if (consumed.isEmpty() || !clock.instant().isBefore(consumed.getFirst().getExpiresAt())) {
            return null;
        }
        return consumed.getFirst();
    }

    /**
     * Adresse du jeton, sans le consommer, s'il est encore valable : la page « Me connecter » l'affiche masquée,
     * pour qu'un bénévole qui ouvre le lien d'un tiers voie qu'il ne s'agit pas de son adresse.
     */
    public Optional<String> peekUsername(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        return jdbc.query("SELECT username FROM one_time_token WHERE token_hash = ? AND expires_at > ?",
                (row, index) -> row.getString("username"), hash(value), Timestamp.from(clock.instant()))
                .stream().findFirst();
    }

    /** Purge des jetons expirés, toutes les heures (minute 23, pour ne pas tomber à l'heure pile). */
    @Scheduled(cron = "0 23 * * * *")
    public void deleteExpiredTokens() {
        jdbc.update("DELETE FROM one_time_token WHERE expires_at <= ?", Timestamp.from(clock.instant()));
    }

    static String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
