package com.booking.engine.security;

import com.booking.engine.properties.TrustedProxyProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Matches remote addresses against the configured trusted proxy allowlist.
 */
@Component
public class TrustedProxyMatcher {

    private final List<AddressRule> rules;

    public TrustedProxyMatcher(TrustedProxyProperties properties) {
        this.rules = properties.getTrustedProxyCidrs().stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .map(this::parseRule)
                .toList();
    }

    public boolean isTrusted(String remoteAddress) {
        InetAddress address = parseAddress(remoteAddress);
        if (address == null) {
            return false;
        }

        for (AddressRule rule : rules) {
            if (rule.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private AddressRule parseRule(String value) {
        String normalized = normalize(value);

        int cidrSeparator = normalized.indexOf('/');
        if (cidrSeparator < 0) {
            InetAddress address = requireAddress(normalized);
            return new AddressRule(address.getAddress(), address.getAddress().length * Byte.SIZE);
        }

        String addressPart = normalized.substring(0, cidrSeparator).trim();
        String prefixPart = normalized.substring(cidrSeparator + 1).trim();
        InetAddress address = requireAddress(addressPart);
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(prefixPart);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid trusted proxy CIDR prefix: " + normalized, exception);
        }

        int maxPrefixLength = address.getAddress().length * Byte.SIZE;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            throw new IllegalStateException("Trusted proxy CIDR prefix is out of range: " + normalized);
        }

        return new AddressRule(address.getAddress(), prefixLength);
    }

    private InetAddress requireAddress(String value) {
        InetAddress address = parseAddress(value);
        if (address == null) {
            throw new IllegalStateException("Invalid trusted proxy address: " + value);
        }
        return address;
    }

    private InetAddress parseAddress(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        try {
            return InetAddress.getByName(normalized);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static final class AddressRule {

        private final byte[] network;
        private final int prefixLength;

        private AddressRule(byte[] network, int prefixLength) {
            this.network = network.clone();
            this.prefixLength = prefixLength;
        }

        private boolean matches(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }

            int wholeBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;

            for (int index = 0; index < wholeBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }

            if (remainingBits == 0 || wholeBytes >= candidate.length) {
                return true;
            }

            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[wholeBytes] & mask) == (network[wholeBytes] & mask);
        }
    }
}
