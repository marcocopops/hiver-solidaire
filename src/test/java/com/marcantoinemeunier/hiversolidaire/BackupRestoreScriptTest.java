package com.marcantoinemeunier.hiversolidaire;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sauvegarde et restauration (S6) : lance les vrais {@code deploy/backup.sh} et {@code deploy/restore.sh}, avec le
 * service restic de {@code deploy/compose.prod.yaml} (même image épinglée), sur une base de l'image PostgreSQL de la
 * production, migrée et remplie par {@code scripts/seed-dev.sql}.
 *
 * <p>Seuls changent : le dépôt restic (un répertoire local au lieu de R2), le conteneur de la base
 * ({@code HS_DB_CONTAINER}), le service de surveillance (un serveur HTTP local qui note les pings) et, pour la
 * restauration en production, un faux conteneur {@code app} (sans appli). Les étapes s'enchaînent dans l'ordre.
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class BackupRestoreScriptTest {

    private static final Path DEPLOY = Path.of("deploy").toAbsolutePath();
    private static final Pattern DB_IMAGE = Pattern.compile("image: (postgres:\\S+)");
    private static final Pattern REPORT_LINE = Pattern.compile("^(\\w+) +(\\S+) +(\\S+)$", Pattern.MULTILINE);
    private static final String MARKER = "restore_marker";

    @TempDir
    static Path work;

    private PostgreSQLContainer postgres;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private HttpServer pingServer;
    private final List<String> pings = new CopyOnWriteArrayList<>();
    private final Map<String, String> environment = new HashMap<>();
    private String databaseImage;

    @BeforeAll
    void start() throws IOException {
        databaseImage = productionDatabaseImage();
        postgres = new PostgreSQLContainer(DockerImageName.parse(databaseImage).asCompatibleSubstituteFor("postgres"));
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var seed = Files.readString(Path.of("scripts/seed-dev.sql"));
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> jdbc.execute(seed));

        pingServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pingServer.createContext("/", exchange -> {
            pings.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        pingServer.start();

        var repository = Files.createDirectory(work.resolve("repository"));
        var backupEnv = work.resolve("backup.env");
        Files.writeString(backupEnv, "RESTIC_REPOSITORY=/repo\nRESTIC_PASSWORD=test-only-not-a-secret\n");
        var override = work.resolve("compose.test.yaml");
        Files.writeString(override, """
                # Test seulement : dépôt restic local, faux conteneur app (sans appli), réseau edge propre au test.
                services:
                  restic:
                    env_file: !override
                      - path: %s
                    volumes:
                      - %s:/repo
                  app:
                    image: %s
                    entrypoint: ["sleep", "infinity"]
                    depends_on: !reset {}
                    healthcheck:
                      test: ["CMD", "true"]
                      interval: 1s
                      start_period: 0s
                networks:
                  edge:
                    external: false
                """.formatted(backupEnv, repository, databaseImage));

        environment.put("COMPOSE_FILE", DEPLOY.resolve("compose.prod.yaml") + ":" + override);
        environment.put("COMPOSE_PROJECT_NAME", "hs-backup-test-" + UUID.randomUUID().toString().substring(0, 8));
        environment.put("POSTGRES_DB", "unused");
        environment.put("POSTGRES_USER", "unused");
        environment.put("POSTGRES_PASSWORD", "unused");
        environment.put("HS_DB_CONTAINER", postgres.getContainerId());
        environment.put("HS_BACKUP_CHECK", "always");
        environment.put("BACKUP_PING_URL",
                "http://127.0.0.1:" + pingServer.getAddress().getPort() + "/ping/backup-uuid");
    }

    @AfterAll
    void stop() {
        try {
            run(List.of("docker", "compose", "--profile", "backup", "down", "--volumes", "--remove-orphans"), "");
        } finally {
            if (pingServer != null) {
                pingServer.stop(0);
            }
            if (postgres != null) {
                postgres.stop();
            }
        }
    }

    @BeforeEach
    void clearPings() {
        pings.clear();
    }

    @Test
    @Order(1)
    void initialises_the_repository() {
        var result = script("backup.sh", List.of("--init"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    @Test
    @Order(2)
    void backs_up_and_reports_success_to_the_monitor() {
        var result = script("backup.sh", List.of(), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(pings).containsExactly("/ping/backup-uuid/start", "/ping/backup-uuid/0");
        assertThat(snapshots()).hasSize(1);
        assertThat(result.output()).contains("Vérification du dépôt").doesNotContain("ERREUR");
        assertNoPlainDumpLeft();
    }

    @Test
    @Order(3)
    void retention_groups_backups_under_one_host() {
        for (int i = 0; i < 2; i++) {
            var result = script("backup.sh", List.of(), "");
            assertThat(result.exitCode()).as(result.output()).isZero();
        }

        // Trois sauvegardes le même jour : restic garde la dernière du jour et la plus ancienne (tranches pas encore
        // remplies), pas celle du milieu. Le nom d'hôte ne change pas d'un conteneur à l'autre : sinon « forget » ne
        // regrouperait rien et le dépôt grossirait sans fin.
        var snapshots = snapshots();
        assertThat(snapshots).hasSize(2)
                .allSatisfy(snapshot -> assertThat(snapshot).contains("\"hostname\":\"hiver-solidaire\""));
    }

    @Test
    @Order(4)
    void a_backup_without_forget_keeps_every_snapshot() {
        var result = script("backup.sh", List.of("--no-forget"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).doesNotContain("Rétention");
        assertThat(snapshots()).hasSize(3);
        assertThat(script("backup.sh", List.of(), "").exitCode()).isZero();
        assertThat(snapshots()).hasSize(2);
    }

    @Test
    @Order(5)
    void restores_the_latest_backup_into_a_throwaway_database_with_the_same_counts() {
        var expected = expectedCounts();

        var result = script("restore.sh", List.of(), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Contrôle réussi");
        var report = report(result.output());
        assertThat(report.keySet()).containsAll(expected.keySet());
        expected.forEach((key, value) -> {
            assertThat(report.get(key)[0]).as("sauvegarde " + key).isEqualTo(value);
            assertThat(report.get(key)[1]).as("production " + key).isEqualTo(value);
        });
        assertThat(Integer.parseInt(expected.get("registrations"))).isPositive();
        assertThat(throwawayContainers()).isEmpty();
        assertNoPlainDumpLeft();
    }

    @Test
    @Order(6)
    void a_failed_backup_is_reported_to_the_monitor_and_leaves_no_dump() {
        var before = snapshots().size();

        var result = script("backup.sh", List.of(), "", Map.of("HS_DB_CONTAINER", "hs-no-such-container"));

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(pings).hasSize(2).first().isEqualTo("/ping/backup-uuid/start");
        assertThat(pings.get(1)).matches("/ping/backup-uuid/[1-9][0-9]*");
        assertThat(result.output()).contains("ÉCHEC de la sauvegarde");
        assertThat(snapshots()).hasSize(before);
        assertNoPlainDumpLeft();
    }

    @Test
    @Order(7)
    void an_unknown_backup_is_refused() {
        var result = script("restore.sh", List.of("0123abcd"), "");

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(throwawayContainers()).isEmpty();
    }

    @Test
    @Order(8)
    void production_restore_without_the_right_confirmation_changes_nothing() {
        jdbc.execute("CREATE TABLE " + MARKER + " (id int)");
        try {
            var result = script("restore.sh", List.of("--production"), "pas-la-bonne-base\n");

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).contains("confirmation incorrecte");
            assertThat(markerExists()).isTrue();
        } finally {
            jdbc.execute("DROP TABLE " + MARKER);
        }
    }

    @Test
    @Order(9)
    void production_restore_backs_up_first_then_replaces_the_database() {
        var expected = expectedCounts();
        jdbc.execute("CREATE TABLE " + MARKER + " (id int)");
        jdbc.update("UPDATE registration SET cancelled_at = now() WHERE cancelled_at IS NULL");

        var result = script("restore.sh", List.of("--production"), postgres.getDatabaseName() + "\n");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).containsPattern("Sauvegarde préalable : [0-9a-f]{8}")
                .contains("Restauration terminée");
        // La sauvegarde préalable n'a pas été celle qu'on restaure : l'état modifié a disparu.
        assertThat(markerExists()).isFalse();
        assertThat(expectedCounts()).isEqualTo(expected);
        // Le faux conteneur app a été arrêté puis relancé, « healthy ».
        var app = run(List.of("docker", "compose", "ps", "--status", "running", "-q", "app"), "");
        assertThat(app.output().strip()).isNotEmpty();
    }

    // --- Outils ---------------------------------------------------------------------------------------------------

    private record Result(int exitCode, String output) {
    }

    private static String productionDatabaseImage() throws IOException {
        var matcher = DB_IMAGE.matcher(Files.readString(DEPLOY.resolve("compose.prod.yaml")));
        assertThat(matcher.find()).as("image PostgreSQL de compose.prod.yaml").isTrue();
        // « nom:tag@empreinte » : Testcontainers attend « nom@empreinte ».
        return matcher.group(1).replaceFirst(":[^@]+@", "@");
    }

    /** Mesures de restore-check.sql, calculées par le test sur la base source. */
    private Map<String, String> expectedCounts() {
        var counts = new LinkedHashMap<String, String>();
        counts.put("flyway_failed", "0");
        counts.put("seasons", count("SELECT count(*) FROM season"));
        counts.put("evenings", count("SELECT count(*) FROM evening"));
        counts.put("volunteers", count("SELECT count(*) FROM volunteer WHERE deleted_at IS NULL"));
        counts.put("registrations", count("SELECT count(*) FROM registration WHERE cancelled_at IS NULL"));
        counts.put("audit_log", count("SELECT count(*) FROM audit_log"));
        counts.put("mail_delivery", count("SELECT count(*) FROM mail_delivery"));
        counts.put("flyway_version", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success "
                + "AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class));
        return counts;
    }

    private String count(String sql) {
        return String.valueOf(jdbc.queryForObject(sql, Long.class));
    }

    private boolean markerExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, MARKER));
    }

    /** Lignes « mesure sauvegarde production » du rapport de restore.sh. */
    private static Map<String, String[]> report(String output) {
        var report = new HashMap<String, String[]>();
        var matcher = REPORT_LINE.matcher(output);
        while (matcher.find()) {
            report.put(matcher.group(1), new String[] {matcher.group(2), matcher.group(3)});
        }
        return report;
    }

    private List<String> snapshots() {
        var result = run(List.of("docker", "compose", "--profile", "backup", "run", "--rm", "--no-deps", "-T",
                "--user", uid(), "restic", "snapshots", "--json"), "");
        assertThat(result.exitCode()).as(result.output()).isZero();
        var json = result.output().lines().filter(line -> line.startsWith("[")).findFirst().orElseThrow();
        var snapshots = new ArrayList<String>();
        var matcher = Pattern.compile("\\{\"time\".*?\"short_id\":\"[0-9a-f]+\"}").matcher(json);
        while (matcher.find()) {
            snapshots.add(matcher.group());
        }
        return snapshots;
    }

    private String uid() {
        var id = run(List.of("id", "-u"), "").output().strip();
        var gid = run(List.of("id", "-g"), "").output().strip();
        return id + ":" + gid;
    }

    private List<String> throwawayContainers() {
        var result = run(List.of("docker", "ps", "-aq", "--filter", "name=hiver-solidaire-restore-check"), "");
        return result.output().lines().filter(line -> !line.isBlank()).toList();
    }

    private static void assertNoPlainDumpLeft() {
        try (Stream<Path> files = Files.list(Path.of("/dev/shm"))) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith("hiver-solidaire-backup."));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Result script(String name, List<String> arguments, String input) {
        return script(name, arguments, input, Map.of());
    }

    private Result script(String name, List<String> arguments, String input, Map<String, String> overrides) {
        var command = new ArrayList<String>();
        command.add(DEPLOY.resolve(name).toString());
        command.addAll(arguments);
        return run(command, input, overrides);
    }

    private Result run(List<String> command, String input) {
        return run(command, input, Map.of());
    }

    private Result run(List<String> command, String input, Map<String, String> overrides) {
        var builder = new ProcessBuilder(command).redirectErrorStream(true).directory(DEPLOY.toFile());
        builder.environment().putAll(environment);
        builder.environment().putAll(overrides);
        try {
            var process = builder.start();
            var output = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new AssertionError("délai dépassé : " + command);
            }
            return new Result(process.exitValue(), output.join());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
