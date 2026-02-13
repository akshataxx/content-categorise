package com.app.categorise.api.dto;

import java.util.List;

/**
 * Paginated list response for the jobs endpoint.
 * Matches the JobListResponse schema in contracts/jobs-api.yaml.
 */
public record JobListResponse(
    List<JobStatusDto> jobs,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
