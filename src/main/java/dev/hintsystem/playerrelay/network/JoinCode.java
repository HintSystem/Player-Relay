package dev.hintsystem.playerrelay.network;

import dev.hintsystem.playerrelay.PlayerRelay;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class JoinCode {
    private static final String PREFIX = "j:";
    private static final String VERSION_SECRET = loadSecret();

    private static final int NONCE_LENGTH = 6;
    private static final int TAG_BITS = 96;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String create(int version, InetAddress address, int port, String password) throws Exception {
        byte[] key = deriveKey(password);
        byte[] addressBytes = address.getAddress();

        ByteBuffer payload = ByteBuffer.allocate(
            1 + addressBytes.length + 2
        );

        payload.put((byte) addressBytes.length);
        payload.put(addressBytes);
        payload.putShort((short) port);

        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(TAG_BITS, nonce)
        );

        byte[] encrypted = cipher.doFinal(payload.array());

        ByteBuffer result = ByteBuffer.allocate(
            1 + NONCE_LENGTH + encrypted.length
        );

        result.put((byte) version);
        result.put(nonce);
        result.put(encrypted);

        return PREFIX + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(result.array());
    }

    public static InetSocketAddress decode(int version, String code, String password) throws Exception {
        if (!isJoinCode(code)) {
            throw new Exception("Not an identifiable join code");
        }

        String raw = code.substring(PREFIX.length());
        byte[] data = Base64.getUrlDecoder().decode(raw);

        if (data.length < 1 + NONCE_LENGTH + 16) {
            throw new Exception("Invalid join code data length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        int decodedVersion = buffer.get() & 0xFF;
        if (decodedVersion != version) {
            throw new Exception("Invalid join code version");
        }

        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);

        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        byte[] key = deriveKey(password);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(TAG_BITS, nonce)
        );

        byte[] decrypted = cipher.doFinal(encrypted);

        ByteBuffer payload = ByteBuffer.wrap(decrypted);

        int addressLength = payload.get() & 0xFF;
        if (addressLength != 4 && addressLength != 16) {
            throw new Exception("Invalid join code address");
        }

        byte[] addressBytes = new byte[addressLength];
        payload.get(addressBytes);

        InetAddress address = InetAddress.getByAddress(addressBytes);
        int port = payload.getShort() & 0xFFFF;

        return new InetSocketAddress(address, port);
    }

    public static boolean isJoinCode(String value) {
        return value != null
            && value.startsWith(PREFIX)
            && value.length() > PREFIX.length();
    }

    /** @return -1 if invalid */
    public static int getVersion(String code) {
        if (!isJoinCode(code)) return -1;

        try {
            byte[] data = Base64.getUrlDecoder()
                .decode(code.substring(PREFIX.length()));

            if (data.length == 0) return -1;

            return data[0] & 0xFF;
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] deriveKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        digest.update(
            VERSION_SECRET.getBytes(StandardCharsets.UTF_8)
        );

        if (password != null && !password.isEmpty()) {
            digest.update(
                password.getBytes(StandardCharsets.UTF_8)
            );
        }

        byte[] hash = digest.digest();

        // AES-128
        byte[] key = new byte[16];
        System.arraycopy(hash, 0, key, 0, 16);

        return key;
    }

    private static String loadSecret() {
        try (InputStream stream = JoinCode.class.getResourceAsStream("/joincode.secret")) {
            if (stream == null) throw new RuntimeException("Missing joincode.secret");

            return new String(
                stream.readAllBytes(),
                StandardCharsets.UTF_8
            ).trim();
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to load join code secret, might not be able to decrypt join codes:\n{}", e.toString());
            return "no-secret";
        }
    }
}