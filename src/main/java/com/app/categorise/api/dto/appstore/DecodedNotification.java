package com.app.categorise.api.dto.appstore;

/**
 * Decoded ASN v2 notification structure.
 * Contains the parsed fields from Apple's JWS-signed notification payload.
 */
public class DecodedNotification {

    private String notificationType;
    private String subtype;
    private String notificationUUID;
    private String environment;
    private String bundleId;
    private String signedTransactionInfo;
    private String signedRenewalInfo;

    public DecodedNotification() {}

    private DecodedNotification(Builder builder) {
        this.notificationType = builder.notificationType;
        this.subtype = builder.subtype;
        this.notificationUUID = builder.notificationUUID;
        this.environment = builder.environment;
        this.bundleId = builder.bundleId;
        this.signedTransactionInfo = builder.signedTransactionInfo;
        this.signedRenewalInfo = builder.signedRenewalInfo;
    }

    public static Builder builder() { return new Builder(); }

    public String getNotificationType() { return notificationType; }
    public String getSubtype() { return subtype; }
    public String getNotificationUUID() { return notificationUUID; }
    public String getEnvironment() { return environment; }
    public String getBundleId() { return bundleId; }
    public String getSignedTransactionInfo() { return signedTransactionInfo; }
    public String getSignedRenewalInfo() { return signedRenewalInfo; }

    public static class Builder {
        private String notificationType;
        private String subtype;
        private String notificationUUID;
        private String environment;
        private String bundleId;
        private String signedTransactionInfo;
        private String signedRenewalInfo;

        public Builder notificationType(String notificationType) { this.notificationType = notificationType; return this; }
        public Builder subtype(String subtype) { this.subtype = subtype; return this; }
        public Builder notificationUUID(String notificationUUID) { this.notificationUUID = notificationUUID; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder bundleId(String bundleId) { this.bundleId = bundleId; return this; }
        public Builder signedTransactionInfo(String signedTransactionInfo) { this.signedTransactionInfo = signedTransactionInfo; return this; }
        public Builder signedRenewalInfo(String signedRenewalInfo) { this.signedRenewalInfo = signedRenewalInfo; return this; }

        public DecodedNotification build() { return new DecodedNotification(this); }
    }
}
