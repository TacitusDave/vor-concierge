package com.vorconcierge.ragcore.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vorconcierge.ragcore.config.ConciergeProperties;
import com.vorconcierge.ragcore.model.AuthProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final int expirationHours;

    public JwtService(ConciergeProperties properties, ResourceLoader resourceLoader) {
        this.expirationHours = properties.jwt().expirationHours();
        try {
            String configuredPrivate = properties.jwt().privateKeyPath();
            String configuredPublic = properties.jwt().publicKeyPath();
            if (configuredPrivate != null && !configuredPrivate.isBlank()
                    && configuredPublic != null && !configuredPublic.isBlank()) {
                Resource privateRes = resourceLoader.getResource(configuredPrivate);
                Resource publicRes = resourceLoader.getResource(configuredPublic);
                if (!privateRes.exists() || !publicRes.exists()) {
                    throw new IllegalStateException(
                            "Configured JWT key path does not exist: " + configuredPrivate + " / " + configuredPublic);
                }
                this.privateKey = loadPrivateKey(privateRes.getInputStream());
                this.publicKey = loadPublicKey(publicRes.getInputStream());
                log.info("Loaded JWT signing key from configured path");
            } else {
                KeyPair pair = loadOrGeneratePersistedKeyPair(properties.jwt().generatedKeyPath());
                this.privateKey = (RSAPrivateKey) pair.getPrivate();
                this.publicKey = (RSAPublicKey) pair.getPublic();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT keys", e);
        }
    }

    /**
     * No externally managed key was configured. Reuse a previously generated key from disk if
     * present so restarts don't invalidate every session; otherwise generate and persist one.
     * This is a single-instance bootstrap convenience — deployments running more than one backend
     * instance must configure a shared key via concierge.jwt.private-key-path/public-key-path.
     */
    private KeyPair loadOrGeneratePersistedKeyPair(String generatedKeyPath) throws Exception {
        Path dir = Path.of(generatedKeyPath);
        Path privatePath = dir.resolve("generated-private.pem");
        Path publicPath = dir.resolve("generated-public.pem");

        if (Files.exists(privatePath) && Files.exists(publicPath)) {
            RSAPrivateKey priv = loadPrivateKey(Files.newInputStream(privatePath));
            RSAPublicKey pub = loadPublicKey(Files.newInputStream(publicPath));
            log.info("Loaded persisted JWT signing key from {}", dir.toAbsolutePath());
            return new KeyPair(pub, priv);
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        Files.createDirectories(dir);
        Files.writeString(privatePath, toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicPath, toPem("PUBLIC KEY", pair.getPublic().getEncoded()));

        log.warn("""

                ================================================================
                 No JWT signing key configured (concierge.jwt.private-key-path).
                 Generated a new RSA key pair and persisted it to: {}
                 This is safe for a single backend instance. Before running more
                 than one instance, or before production go-live, configure a
                 centrally managed key via JWT_PRIVATE_KEY / JWT_PUBLIC_KEY.
                ================================================================
                """, dir.toAbsolutePath());

        return pair;
    }

    private String toPem(String label, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }

    public String generateToken(AuthProfile profile) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationHours * 3600L);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(profile.username())
                .claim("uid", profile.userId())
                .claim("org", profile.orgId())
                .claim("rol", profile.roleId())
                .claim("lvl", profile.hierarchyLevel())
                .claim("cap", profile.roleCapabilityMask())
                .claim("plt", profile.isPlatformOperator())
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry));
        if (profile.departmentId() != null) {
            builder.claim("dep", profile.departmentId());
        }
        JWTClaimsSet claims = builder.build();

        try {
            SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            signed.sign(new RSASSASigner(privateKey));
            return signed.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public AuthenticatedUser validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new SecurityException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                throw new SecurityException("JWT expired");
            }
            Long userId = claims.getLongClaim("uid");
            String username = claims.getSubject();
            Long orgId = claims.getLongClaim("org");
            Long departmentId = claims.getLongClaim("dep");
            Long roleId = claims.getLongClaim("rol");
            Integer hierarchyLevel = claims.getIntegerClaim("lvl");
            Long capabilityMask = claims.getLongClaim("cap");
            Boolean platformOperator = claims.getBooleanClaim("plt");
            String jti = claims.getJWTID();
            if (userId == null || username == null || orgId == null || roleId == null
                    || hierarchyLevel == null || capabilityMask == null || platformOperator == null) {
                throw new SecurityException("Missing JWT claims");
            }
            return new AuthenticatedUser(userId, username, orgId, departmentId, roleId,
                    hierarchyLevel, capabilityMask, platformOperator, jti);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Invalid JWT", e);
        }
    }

    private RSAPrivateKey loadPrivateKey(InputStream in) throws Exception {
        String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private RSAPublicKey loadPublicKey(InputStream in) throws Exception {
        String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }
}
