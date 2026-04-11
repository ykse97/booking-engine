package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FlywayBaselineMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[BV](\\d+)__.*\\.sql$");
    private static final String LEGACY_ADMIN_HASH = "$2a$10$/b8Kp9eds4FUCFT6ETS9jO2HiucVz4k3MUBdkP1BNCmX9iHZKJUmW";

    @Test
    void latestBaselineMatchesLatestVersionedMigration() throws IOException {
        Path latestVersioned = latestMigrationWithPrefix("V");
        Path latestBaseline = latestMigrationWithPrefix("B");

        assertThat(latestBaseline).isNotNull();
        assertThat(extractVersion(latestBaseline)).isEqualTo(extractVersion(latestVersioned));
    }

    @Test
    void latestBaselineDoesNotContainSeededAdminCredentials() throws IOException {
        Path latestBaseline = latestMigrationWithPrefix("B");
        String sql = Files.readString(latestBaseline);

        assertThat(sql).doesNotContain("INSERT INTO admin_user");
        assertThat(sql).doesNotContain(LEGACY_ADMIN_HASH);
        assertThat(sql).doesNotContain("is_barber BOOLEAN");
        assertThat(sql).contains("bookable BOOLEAN NOT NULL DEFAULT FALSE");
    }

    private Path latestMigrationWithPrefix(String prefix) throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .max(Comparator.comparingInt(this::extractVersion))
                    .orElseThrow(() -> new IllegalStateException("No " + prefix + " migrations found"));
        }
    }

    private int extractVersion(Path path) {
        Matcher matcher = VERSION_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected migration filename: " + path.getFileName());
        }
        return Integer.parseInt(matcher.group(1));
    }
}
