package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mise à jour en pull (S6, ADR-0015) : {@code deploy/update.sh} relance les conteneurs avec {@code compose.prod.yaml},
 * puis compare les fichiers du serveur à ceux de l'image en service ({@code /app/deploy}) et signale l'écart, sans rien
 * remplacer. Le conteneur {@code app} est factice : l'image PostgreSQL de la production, endormie, avec les fichiers
 * de référence montés à la place de ceux qu'embarque la vraie image.
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class UpdateScriptTest {

    private static final Pattern ALLOWED = Pattern.compile("^!deploy/(\\S+)$", Pattern.MULTILINE);
    private static final String ADDED_LINE = "# ligne ajoutée par une nouvelle version";

    @TempDir
    static Path work;

    private final ScriptRunner scripts = new ScriptRunner();
    private final List<String> pings = new CopyOnWriteArrayList<>();
    private HttpServer pingServer;
    private Path reference;

    @BeforeAll
    void start() throws IOException {
        pingServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pingServer.createContext("/", exchange -> {
            pings.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        pingServer.start();

        // Ce que la vraie image contient : les fichiers de la liste blanche de .dockerignore.
        reference = Files.createDirectory(work.resolve("reference"));
        for (var name : imageFiles()) {
            Files.copy(ScriptRunner.DEPLOY.resolve(name), reference.resolve(name));
        }

        var compose = Files.readString(ScriptRunner.DEPLOY.resolve("compose.prod.yaml"));
        var image = Pattern.compile("image: (postgres:\\S+)").matcher(compose);
        assertThat(image.find()).isTrue();
        var override = work.resolve("compose.test.yaml");
        Files.writeString(override, """
                # Test seulement : faux conteneur app (sans appli) qui porte les fichiers de référence.
                services:
                  app:
                    image: %s
                    entrypoint: ["sleep", "infinity"]
                    depends_on: !reset {}
                    healthcheck:
                      test: ["CMD", "true"]
                      interval: 1s
                    volumes:
                      - %s:/app/deploy:ro
                networks:
                  edge:
                    external: false
                """.formatted(image.group(1), reference));

        scripts.put("COMPOSE_FILE", ScriptRunner.DEPLOY.resolve("compose.prod.yaml") + ":" + override)
                .put("COMPOSE_PROJECT_NAME", "hs-update-test-" + UUID.randomUUID().toString().substring(0, 8))
                .put("POSTGRES_DB", "unused")
                .put("POSTGRES_USER", "unused")
                .put("POSTGRES_PASSWORD", "unused-test-only")
                .put("HS_UPDATE_STATE", work.resolve("update-drift").toString())
                .put("UPDATE_PING_URL", "http://127.0.0.1:" + pingServer.getAddress().getPort() + "/ping/update-uuid");
    }

    @AfterAll
    void stop() {
        try {
            scripts.run(List.of("docker", "compose", "down", "--volumes", "--remove-orphans"));
        } finally {
            if (pingServer != null) {
                pingServer.stop(0);
            }
        }
    }

    @BeforeEach
    void clearPings() {
        pings.clear();
    }

    /** Un fichier de deploy/ oublié dans la liste blanche ne serait jamais comparé : la liste suit le dépôt. */
    @Test
    @Order(1)
    void the_image_carries_every_deployment_file() throws IOException {
        List<String> files;
        try (var listing = Files.list(ScriptRunner.DEPLOY)) {
            // Hors documentation, fichiers cachés (état d'update.sh) et secrets du serveur (non versionnés).
            files = listing.map(path -> path.getFileName().toString())
                    .filter(name -> !name.equals("README.md") && !name.startsWith(".") && !name.equals("backup.env"))
                    .toList();
        }

        assertThat(imageFiles()).containsExactlyInAnyOrderElementsOf(files);
    }

    @Test
    @Order(2)
    void starts_the_containers_and_reports_success() {
        var result = scripts.script("update.sh", List.of(), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Conteneurs relancés").doesNotContain("ÉCART");
        assertThat(pings).containsExactly("/ping/update-uuid/0");
    }

    @Test
    @Order(3)
    void stays_silent_when_nothing_changes() {
        var result = scripts.script("update.sh", List.of(), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).isEmpty();
        assertThat(pings).containsExactly("/ping/update-uuid/0");
    }

    @Test
    @Order(4)
    void reports_a_drifted_file_without_replacing_it() throws IOException {
        var local = Files.readString(ScriptRunner.DEPLOY.resolve("compose.prod.yaml"));
        Files.writeString(reference.resolve("compose.prod.yaml"), "\n" + ADDED_LINE + "\n", StandardOpenOption.APPEND);

        var result = scripts.script("update.sh", List.of(), "");

        assertThat(result.exitCode()).as(result.output()).isEqualTo(3);
        assertThat(result.output()).contains("ÉCART").contains("  compose.prod.yaml").doesNotContain("lib.sh");
        assertThat(pings).containsExactly("/ping/update-uuid/3");
        assertThat(Files.readString(ScriptRunner.DEPLOY.resolve("compose.prod.yaml"))).isEqualTo(local);
    }

    @Test
    @Order(5)
    void does_not_repeat_the_same_drift_in_the_log_but_keeps_alerting() {
        var result = scripts.script("update.sh", List.of(), "");

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.output()).isEmpty();
        assertThat(pings).containsExactly("/ping/update-uuid/3");
    }

    @Test
    @Order(6)
    void shows_the_difference_on_demand() {
        var result = scripts.script("update.sh", List.of("--diff"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("+++ version en service/compose.prod.yaml").contains("+" + ADDED_LINE);
        assertThat(pings).isEmpty();
    }

    @Test
    @Order(7)
    void reports_when_the_drift_is_gone() throws IOException {
        Files.copy(ScriptRunner.DEPLOY.resolve("compose.prod.yaml"), reference.resolve("compose.prod.yaml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        var result = scripts.script("update.sh", List.of(), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Plus d'écart");
        assertThat(pings).containsExactly("/ping/update-uuid/0");
    }

    /** Fichiers de deploy/ que .dockerignore laisse entrer dans l'image. */
    private static List<String> imageFiles() {
        try {
            Matcher matcher = ALLOWED.matcher(Files.readString(Path.of(".dockerignore")));
            return matcher.results().map(match -> match.group(1)).toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
