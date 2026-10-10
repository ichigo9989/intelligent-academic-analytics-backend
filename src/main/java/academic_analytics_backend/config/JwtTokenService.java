package academic_analytics_backend.config;

import academic_analytics_backend.entity.AppUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final long TOKEN_LIFETIME_SECONDS = 60L * 60L * 12L;
    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final JsonMapper objectMapper;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret, JsonMapper objectMapper) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes.");
        }
        this.objectMapper = objectMapper;
    }

    public String issue(AppUser user) {
        long now = Instant.now().getEpochSecond();
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.getEmail(),
                    "iat", now,
                    "exp", now + TOKEN_LIFETIME_SECONDS
            ));
            String signingInput = HEADER + "." + encode(payload);
            return signingInput + "." + encode(sign(signingInput));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token.", exception);
        }
    }

    public String subject(String token) {
        try {
            String[] pieces = token.split("\\.", -1);
            if (pieces.length != 3) throw new IllegalArgumentException("Malformed token.");
            String signingInput = pieces[0] + "." + pieces[1];
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(pieces[2]);
            if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
                throw new IllegalArgumentException("Invalid token signature.");
            }
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(pieces[1]));
            if (!payload.hasNonNull("sub") || !payload.hasNonNull("exp")
                    || payload.get("exp").asLong() <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Expired or incomplete token.");
            }
            return payload.get("sub").asText();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid access token.", exception);
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
