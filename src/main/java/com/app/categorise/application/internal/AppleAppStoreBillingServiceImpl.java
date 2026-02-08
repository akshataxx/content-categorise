package com.app.categorise.application.internal;

import com.app.categorise.api.dto.subscription.apple.AppStoreSubscriptionStatus;
import com.app.categorise.api.dto.subscription.apple.AppStoreVerificationResult;
import com.app.categorise.api.dto.subscription.apple.DecodedTransaction;
import com.app.categorise.config.AppleAppStoreConfiguration;
import com.app.categorise.domain.service.AppleAppStoreBillingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Implementation of AppleAppStoreBillingService.
 * Verifies App Store transactions using Apple's App Store Server API.
 */
@Service
public class AppleAppStoreBillingServiceImpl implements AppleAppStoreBillingService {

    private static final Logger logger = LoggerFactory.getLogger(AppleAppStoreBillingServiceImpl.class);

    private final AppleAppStoreConfiguration config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private PrivateKey privateKey;

    public AppleAppStoreBillingServiceImpl(AppleAppStoreConfiguration config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PostConstruct
    public void init() {
        try {
            if ("xcode-testing".equalsIgnoreCase(config.getEnvironment())) {
                logger.info("Apple App Store Billing service running in XCODE-TESTING mode. " +
                        "Private key not required — transactions will be verified locally.");
                return;
            }

            if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isEmpty()) {
                loadPrivateKey();
                logger.info("Apple App Store Billing service initialized successfully");
            } else {
                logger.warn("Apple App Store private key path not configured. " +
                        "App Store purchase verification will not work until configured.");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Apple App Store Billing service", e);
            // Don't throw - allow application to start without Apple billing
        }
    }

    @Override
    public AppStoreVerificationResult verifyTransaction(String signedTransaction) {
        try {
            // In xcode-testing mode, skip Apple API verification and trust the decoded JWS directly.
            // Xcode's StoreKit testing signs JWS with a local certificate that Apple's API won't recognise.
            if ("xcode-testing".equalsIgnoreCase(config.getEnvironment())) {
                return verifyTransactionLocally(signedTransaction);
            }

            if (privateKey == null) {
                return AppStoreVerificationResult.builder()
                        .verified(false)
                        .errorMessage("Apple App Store Billing service not initialized")
                        .build();
            }

            // Decode transaction to get transaction ID
            DecodedTransaction decoded = decodeTransaction(signedTransaction);

            // Call App Store Server API to get transaction info
            String url = config.getApiBaseUrl() +
                    "/inApps/v1/transactions/" + decoded.getTransactionId();

            String jwt = generateAppStoreJwt();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode responseBody = objectMapper.readTree(response.body());
                String signedTransactionInfo = responseBody.path("signedTransactionInfo").asText();

                DecodedTransaction verified = decodeTransaction(signedTransactionInfo);

                boolean isActive = verified.getExpiresDate() != null
                        && verified.getExpiresDate().isAfter(Instant.now());

                return AppStoreVerificationResult.builder()
                        .verified(true)
                        .subscriptionActive(isActive)
                        .expirationTime(verified.getExpiresDate())
                        .originalTransactionId(verified.getOriginalTransactionId())
                        .transactionId(verified.getTransactionId())
                        .productId(verified.getProductId())
                        .build();
            } else {
                logger.warn("App Store API returned status {}: {}", response.statusCode(), response.body());
                return AppStoreVerificationResult.builder()
                        .verified(false)
                        .errorMessage("Verification failed: HTTP " + response.statusCode())
                        .build();
            }

        } catch (Exception e) {
            logger.error("Failed to verify App Store transaction", e);
            return AppStoreVerificationResult.builder()
                    .verified(false)
                    .errorMessage("Verification failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Verify a transaction locally by decoding the JWS payload without calling Apple's API.
     * Used for Xcode StoreKit Testing where transactions are signed by Xcode's local certificate.
     * NEVER use this in production or sandbox — only for local development.
     */
    private AppStoreVerificationResult verifyTransactionLocally(String signedTransaction) {
        logger.warn("⚠ XCODE-TESTING MODE: Skipping Apple API verification, trusting decoded JWS directly");
        try {
            DecodedTransaction decoded = decodeTransaction(signedTransaction);

            boolean isActive = decoded.getExpiresDate() != null
                    && decoded.getExpiresDate().isAfter(Instant.now());

            logger.info("Xcode-testing verification: productId={}, transactionId={}, originalTransactionId={}, expiresDate={}, active={}",
                    decoded.getProductId(), decoded.getTransactionId(),
                    decoded.getOriginalTransactionId(), decoded.getExpiresDate(), isActive);

            return AppStoreVerificationResult.builder()
                    .verified(true)
                    .subscriptionActive(isActive)
                    .expirationTime(decoded.getExpiresDate())
                    .originalTransactionId(decoded.getOriginalTransactionId())
                    .transactionId(decoded.getTransactionId())
                    .productId(decoded.getProductId())
                    .build();
        } catch (Exception e) {
            logger.error("Failed to decode transaction in xcode-testing mode", e);
            return AppStoreVerificationResult.builder()
                    .verified(false)
                    .errorMessage("Local verification failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public DecodedTransaction decodeTransaction(String signedPayload) {
        try {
            // Split JWS into parts (header.payload.signature)
            String[] parts = signedPayload.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWS format: expected 3 parts, got " + parts.length);
            }

            // Decode header to verify algorithm
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode header = objectMapper.readTree(headerJson);

            String alg = header.has("alg") ? header.get("alg").asText() : "unknown";
            if (!"ES256".equals(alg)) {
                logger.warn("Unexpected JWS algorithm: {}", alg);
            }

            // Decode payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);

            // Extract transaction info
            return DecodedTransaction.builder()
                    .transactionId(getTextOrNull(payload, "transactionId"))
                    .originalTransactionId(getTextOrNull(payload, "originalTransactionId"))
                    .productId(getTextOrNull(payload, "productId"))
                    .purchaseDate(getInstantOrNull(payload, "purchaseDate"))
                    .expiresDate(getInstantOrNull(payload, "expiresDate"))
                    .environment(getTextOrNull(payload, "environment"))
                    .build();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to decode JWS transaction", e);
            throw new RuntimeException("Failed to decode transaction", e);
        }
    }

    @Override
    public AppStoreSubscriptionStatus getSubscriptionStatus(String originalTransactionId) {
        try {
            if (privateKey == null) {
                throw new IllegalStateException("Apple App Store Billing service not initialized");
            }

            String url = config.getApiBaseUrl() +
                    "/inApps/v1/subscriptions/" + originalTransactionId;

            String jwt = generateAppStoreJwt();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode responseBody = objectMapper.readTree(response.body());

                // Parse subscription group info from response
                JsonNode data = responseBody.path("data");
                if (data.isArray() && data.size() > 0) {
                    JsonNode subscriptionGroup = data.get(0);
                    JsonNode lastTransactions = subscriptionGroup.path("lastTransactions");

                    if (lastTransactions.isArray() && lastTransactions.size() > 0) {
                        JsonNode lastTx = lastTransactions.get(0);
                        String status = lastTx.path("status").asText();
                        String signedTransactionInfo = lastTx.path("signedTransactionInfo").asText();

                        DecodedTransaction decoded = decodeTransaction(signedTransactionInfo);

                        return AppStoreSubscriptionStatus.builder()
                                .originalTransactionId(originalTransactionId)
                                .productId(decoded.getProductId())
                                .expiresDate(decoded.getExpiresDate())
                                .autoRenewEnabled(!"2".equals(status)) // status 2 = expired
                                .status(mapAppleStatus(status))
                                .build();
                    }
                }

                return AppStoreSubscriptionStatus.builder()
                        .originalTransactionId(originalTransactionId)
                        .status("UNKNOWN")
                        .build();
            } else {
                logger.warn("App Store subscription status API returned {}: {}",
                        response.statusCode(), response.body());
                throw new RuntimeException("Failed to get subscription status: HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Failed to get App Store subscription status for {}", originalTransactionId, e);
            throw new RuntimeException("Failed to get subscription status", e);
        }
    }

    /**
     * Generate a JWT for authenticating with Apple's App Store Server API.
     * Uses ES256 algorithm as required by Apple.
     */
    private String generateAppStoreJwt() {
        Instant now = Instant.now();

        return Jwts.builder()
                .setHeaderParam("kid", config.getKeyId())
                .setHeaderParam("typ", "JWT")
                .setIssuer(config.getIssuerId())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .setAudience("appstoreconnect-v1")
                .claim("bid", config.getBundleId())
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
    }

    /**
     * Load the private key from the .p8 file specified in configuration.
     */
    private void loadPrivateKey() throws Exception {
        String privateKeyContent = Files.readString(Path.of(config.getPrivateKeyPath()))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        this.privateKey = keyFactory.generatePrivate(keySpec);

        logger.info("Apple App Store private key loaded successfully");
    }

    /**
     * Map Apple's numeric subscription status to a readable string.
     */
    private String mapAppleStatus(String statusCode) {
        return switch (statusCode) {
            case "1" -> "ACTIVE";
            case "2" -> "EXPIRED";
            case "3" -> "BILLING_RETRY";
            case "4" -> "GRACE_PERIOD";
            case "5" -> "REVOKED";
            default -> "UNKNOWN";
        };
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Instant getInstantOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return Instant.ofEpochMilli(node.get(field).asLong());
        }
        return null;
    }
}
