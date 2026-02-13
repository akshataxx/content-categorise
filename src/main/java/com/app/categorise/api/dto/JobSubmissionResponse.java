package com.app.categorise.api.dto;

import java.util.UUID;

public record JobSubmissionResponse(
    UUID jobId,
    String status
) {}
