-- V18__create_devices_table.sql
-- Create devices table for FCM device token storage (push notifications)

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(10) NOT NULL,
    fcm_token VARCHAR(512) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE UNIQUE INDEX idx_devices_fcm_token ON devices(fcm_token);
