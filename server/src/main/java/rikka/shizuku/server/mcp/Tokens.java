package rikka.shizuku.server.mcp;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Pairing-token helpers.
 *
 * A token is 256 bits of {@link SecureRandom}, base64url without padding. Only
 * its SHA-256 hash is ever persisted (see {@link McpPolicy.Agent#tokenHash}),
 * and comparison is constant-time.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    @NonNull
    public static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    @NonNull
    public static String hash(@NonNull String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every Android platform; unreachable.
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of two hex hashes, to avoid timing leaks. */
    public static boolean hashEquals(@NonNull String a, @NonNull String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }
}
