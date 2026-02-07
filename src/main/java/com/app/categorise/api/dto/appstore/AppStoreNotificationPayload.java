package com.app.categorise.api.dto.appstore;

/**
 * Outer wrapper for App Store Server Notifications v2.
 * Apple sends notifications as JWS-signed payloads.
 */
public class AppStoreNotificationPayload {

    private String signedPayload;

    public AppStoreNotificationPayload() {}

    public String getSignedPayload() { return signedPayload; }
    public void setSignedPayload(String signedPayload) { this.signedPayload = signedPayload; }
}
