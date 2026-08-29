package stonytark.cinemarr.core.server;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

public final class SecretRedactor {
    public static String message(Throwable error, String... secrets) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        String message = value.getMessage();
        if (blank(message)) message = value.getClass().getSimpleName();
        return redact(message, secrets);
    }

    public static String redact(String value, String... secrets) {
        String result = value == null ? "" : value;
        if (secrets != null) for (String secret : secrets) {
            if (blank(secret)) continue;
            result = replace(result, secret);
            try {
                URI endpoint = URI.create(secret);
                if (!blank(endpoint.getRawAuthority())) result = replace(result, endpoint.getRawAuthority());
                if (!blank(endpoint.getHost())) result = replace(result, endpoint.getHost());
            } catch (IllegalArgumentException ignored) {
                // Tokens and other opaque secrets are not required to be URIs.
            }
        }
        return result
                .replaceAll("(?i)(X-Plex-Token(?:=|%3[Dd]))[^&\\s]+", "$1<redacted>")
                .replaceAll("(?i)(Authorization:\\s*(?:Bearer|Basic)\\s+)[^\\s]+", "$1<redacted>")
                .replaceAll("(?i)https?://[^\\s]+", "<redacted-endpoint>");
    }

    private static String replace(String value, String secret) {
        String result = value.replace(secret, "<redacted>").replace(urlEncode(secret), "<redacted>");
        return result.replaceAll("(?i)" + java.util.regex.Pattern.quote(secret),
                java.util.regex.Matcher.quoteReplacement("<redacted>"));
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private SecretRedactor() {}
}
