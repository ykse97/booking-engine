package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.booking.engine.properties.TrustedProxyProperties;
import org.junit.jupiter.api.Test;

class TrustedProxyMatcherTest {

    @Test
    void isTrustedMatchesExactIpAndCidrRanges() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.getTrustedProxyCidrs().add("10.0.0.5");
        properties.getTrustedProxyCidrs().add("192.168.1.0/24");
        TrustedProxyMatcher matcher = new TrustedProxyMatcher(properties);

        assertThat(matcher.isTrusted("10.0.0.5")).isTrue();
        assertThat(matcher.isTrusted("192.168.1.42")).isTrue();
        assertThat(matcher.isTrusted("192.168.2.42")).isFalse();
        assertThat(matcher.isTrusted("203.0.113.8")).isFalse();
    }
}
