package com.app.categorise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Apple App Store Server API integration.
 * Properties are loaded from application.properties with prefix "apple.app-store".
 */
@Configuration
@ConfigurationProperties(prefix = "apple.app-store")
public class AppleAppStoreConfiguration {

    /**
     * Bundle ID of the iOS app (e.g., com.raccoon.TranscribeAssistant)
     */
    private String bundleId;

    /**
     * Issuer ID from App Store Connect API Keys
     */
    private String issuerId;

    /**
     * Key ID from App Store Connect API Keys
     */
    private String keyId;

    /**
     * Path to the .p8 private key file
     */
    private String privateKeyPath;

    /**
     * Environment: "sandbox" or "production"
     */
    private String environment = "sandbox";

    /**
     * Monthly product ID
     */
    private String monthlyProductId = "premium_monthly";

    /**
     * Get the App Store Server API base URL based on environment
     */
    public String getApiBaseUrl() {
        return "sandbox".equalsIgnoreCase(environment)
                ? "https://api.storekit-sandbox.itunes.apple.com"
                : "https://api.storekit.itunes.apple.com";
    }

    // Getters and Setters
    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }

    public String getIssuerId() { return issuerId; }
    public void setIssuerId(String issuerId) { this.issuerId = issuerId; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getMonthlyProductId() { return monthlyProductId; }
    public void setMonthlyProductId(String monthlyProductId) { this.monthlyProductId = monthlyProductId; }
}
