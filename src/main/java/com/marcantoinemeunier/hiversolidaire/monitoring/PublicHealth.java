package com.marcantoinemeunier.hiversolidaire.monitoring;

import java.time.Duration;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * État de l'appli pour la sonde externe : l'appli répond (sinon, pas de réponse du tout) et la base répond à
 * {@code SELECT 1} en moins de 2 secondes. Le résultat est gardé {@code app.health.cache-ttl} (10 s par défaut) : la page
 * est publique, et l'appeler en boucle ne doit pas occuper le pool de connexions. Un seul contrôle à la fois.
 */
@Component
public class PublicHealth {

    private static final Logger LOG = LoggerFactory.getLogger(PublicHealth.class);
    private static final int QUERY_TIMEOUT_SECONDS = 2;

    /** Contrôle qui lève une exception si la base ne répond pas. */
    @FunctionalInterface
    interface Check {
        void run();
    }

    private final Check check;
    private final long ttlNanos;
    private final LongSupplier nanoTime;

    private boolean lastResult;
    private long lastCheckNanos;
    private boolean checkedOnce;

    @Autowired
    PublicHealth(DataSource dataSource, @Value("${app.health.cache-ttl:PT10S}") Duration ttl) {
        this(databaseCheck(dataSource), ttl, System::nanoTime);
    }

    PublicHealth(Check check, Duration ttl, LongSupplier nanoTime) {
        this.check = check;
        this.ttlNanos = ttl.toNanos();
        this.nanoTime = nanoTime;
    }

    private static Check databaseCheck(DataSource dataSource) {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return () -> jdbc.queryForObject("SELECT 1", Integer.class);
    }

    /** Vrai si la base a répondu au dernier contrôle, refait au plus une fois par période de cache. */
    public synchronized boolean up() {
        long now = nanoTime.getAsLong();
        if (checkedOnce && now - lastCheckNanos < ttlNanos) {
            return lastResult;
        }
        lastResult = runCheck();
        lastCheckNanos = nanoTime.getAsLong();
        checkedOnce = true;
        return lastResult;
    }

    private boolean runCheck() {
        try {
            check.run();
            return true;
        } catch (RuntimeException e) {
            // Classe de l'exception seulement : le message peut citer l'hôte ou l'utilisateur de la base.
            LOG.warn("Public health check failed: database unavailable ({})", e.getClass().getSimpleName());
            return false;
        }
    }
}
