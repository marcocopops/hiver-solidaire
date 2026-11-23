package com.marcantoinemeunier.hiversolidaire;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lance les scripts d'exploitation de {@code deploy/} (et les commandes Docker qui les entourent) depuis les tests,
 * avec un environnement propre au test : projet Compose distinct, fichiers Compose de test, conteneurs factices.
 */
final class ScriptRunner {

    static final Path DEPLOY = Path.of("deploy").toAbsolutePath();

    record Result(int exitCode, String output) {
    }

    private final Map<String, String> environment = new HashMap<>();

    ScriptRunner put(String name, String value) {
        environment.put(name, value);
        return this;
    }

    Result script(String name, List<String> arguments, String input) {
        return script(name, arguments, input, Map.of());
    }

    Result script(String name, List<String> arguments, String input, Map<String, String> overrides) {
        var command = new ArrayList<String>();
        command.add(DEPLOY.resolve(name).toString());
        command.addAll(arguments);
        return run(command, input, overrides);
    }

    Result run(List<String> command) {
        return run(command, "", Map.of());
    }

    Result run(List<String> command, String input, Map<String, String> overrides) {
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

    /** {@code uid:gid} de l'utilisateur des tests, comme le passent les scripts à {@code docker compose run}. */
    String user() {
        return run(List.of("id", "-u")).output().strip() + ":" + run(List.of("id", "-g")).output().strip();
    }
}
