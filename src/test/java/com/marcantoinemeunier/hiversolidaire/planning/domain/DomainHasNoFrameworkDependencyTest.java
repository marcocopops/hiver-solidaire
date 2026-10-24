package com.marcantoinemeunier.hiversolidaire.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Le domaine (règles R1 à R7) reste en Java pur : ni Spring, ni JPA, ni Hibernate (CLAUDE.md). */
class DomainHasNoFrameworkDependencyTest {

    private static final Path DOMAIN_SOURCES =
            Path.of("src/main/java/com/marcantoinemeunier/hiversolidaire/planning/domain");

    private static final List<String> FORBIDDEN_IMPORTS =
            List.of("import org.springframework", "import jakarta.", "import org.hibernate");

    @Test
    void domain_sources_do_not_import_frameworks() throws IOException {
        assertThat(DOMAIN_SOURCES).isDirectory();
        try (Stream<Path> files = Files.walk(DOMAIN_SOURCES)) {
            var sources = files.filter(path -> path.toString().endsWith(".java")).toList();

            assertThat(sources).isNotEmpty();
            for (Path source : sources) {
                var content = Files.readString(source);
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    assertThat(content).as(source + " ne doit pas contenir " + forbidden).doesNotContain(forbidden);
                }
            }
        }
    }
}
