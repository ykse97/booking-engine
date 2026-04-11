package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogSanitizerTest {

    @Test
    void sanitizeForLogsMasksEmailsAndHashesPhoneNumbers() {
        String sanitized = SensitiveLogSanitizer.sanitizeForLogs(
                "Customer john.doe@example.com requested call back on +353 87 123 4567");

        assertThat(sanitized).contains("[emailMask=j***e@example.com]");
        assertThat(sanitized).contains("[phoneHash=");
        assertThat(sanitized).doesNotContain("john.doe@example.com");
        assertThat(sanitized).doesNotContain("+353 87 123 4567");
    }

    @Test
    void sanitizeForLogsKeepsDatesVisibleWhileRedactingContactValues() {
        String sanitized = SensitiveLogSanitizer.sanitizeForLogs(
                "Rejected value [2026-04-21] for bookingDate and phone [353871234567]");

        assertThat(sanitized).contains("2026-04-21");
        assertThat(sanitized).contains("[phoneHash=");
        assertThat(sanitized).doesNotContain("353871234567");
    }
}
