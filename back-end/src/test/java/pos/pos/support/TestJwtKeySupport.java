package pos.pos.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class TestJwtKeySupport {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final String PRIVATE_KEY_PEM = toPem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded());
    private static final String PUBLIC_KEY_PEM = toPem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded());

    private TestJwtKeySupport() {
    }

    public static void registerJwtProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_PRIVATE_KEY", TestJwtKeySupport::privateKeyPem);
        registry.add("JWT_PUBLIC_KEY", TestJwtKeySupport::publicKeyPem);
    }

    public static String privateKeyPem() {
        return PRIVATE_KEY_PEM;
    }

    public static String publicKeyPem() {
        return PUBLIC_KEY_PEM;
    }

    public static RSAPrivateKey privateKey() {
        return (RSAPrivateKey) KEY_PAIR.getPrivate();
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair for tests", ex);
        }
    }

    private static String toPem(String type, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n"
                + base64
                + "\n-----END " + type + "-----";
    }
}
