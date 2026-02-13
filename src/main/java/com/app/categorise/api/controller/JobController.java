package com.app.categorise.api.controller;

import com.app.categorise.api.dto.JobListResponse;
import com.app.categorise.api.dto.JobStatusDto;
import com.app.categorise.application.mapper.JobMapper;
import com.app.categorise.data.entity.TranscriptionJobEntity;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.domain.model.JobStatus;
import com.app.categorise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Jobs", description = "Transcription job status tracking")
@RequestMapping("/api/video/jobs")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final TranscriptionJobRepository jobRepository;
    private final JobMapper jobMapper;

    public JobController(TranscriptionJobRepository jobRepository, JobMapper jobMapper) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
    }

    /**
     * List the authenticated user's transcription jobs with optional filters.
     * Results are ordered by createdAt descending (newest first).
     *
     * @param principal authenticated user
     * @param status    optional status filter (PENDING, PROCESSING, COMPLETED, FAILED)
     * @param from      optional start of date range (ISO 8601)
     * @param to        optional end of date range (ISO 8601)
     * @param page      page number (0-indexed, default 0)
     * @param size      page size (default 20, max 100)
     * @return paginated list of jobs
     */
    @Operation(summary = "List transcription jobs", description = "Returns user's jobs with optional status and date filters")
    @GetMapping
    public ResponseEntity<JobListResponse> listJobs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = principal.getId();
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        JobStatus jobStatus = null;
        if (status != null) {
            try {
                jobStatus = JobStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        Page<TranscriptionJobEntity> jobPage = jobRepository.findByUserIdFiltered(userId, jobStatus, from, to, pageRequest);

        List<JobStatusDto> jobs = jobPage.getContent().stream()
                .map(jobMapper::toDto)
                .toList();

        logger.info("Listed {} jobs for user {} (page={}, status={})", jobs.size(), userId, page, status);

        return ResponseEntity.ok(new JobListResponse(
                jobs, jobPage.getNumber(), jobPage.getSize(),
                jobPage.getTotalElements(), jobPage.getTotalPages()));
    }

    /**
     * Get detailed status for a single transcription job.
     * Returns 404 if the job doesn't exist or isn't owned by the authenticated user
     * (don't leak existence via 403).
     *
     * @param principal authenticated user
     * @param jobId     the job UUID
     * @return job details or 404
     */
    @Operation(summary = "Get job status", description = "Returns status for a specific job owned by the authenticated user")
    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusDto> getJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID jobId) {

        UUID userId = principal.getId();

        return jobRepository.findById(jobId)
                .filter(job -> job.getUserId().equals(userId)) // Security: only own jobs
                .map(jobMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
