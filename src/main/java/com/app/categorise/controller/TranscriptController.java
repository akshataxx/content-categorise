package com.app.categorise.controller;

import com.app.categorise.models.entity.Transcript;
import com.app.categorise.service.TranscriptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/transcript")
public class TranscriptController {

    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }


    /**
     * Retrieve a list of transcripts. If {@code id} is provided, will return that
     * specific transcript if it exists. Otherwise, will return all transcripts that
     * match the given filters.
     *
     * @param id the ID of the transcript to retrieve
     * @param categories the categories to filter by
     * @param account the account to filter by
     * @param from the lower bound of the {@code Instant} range to filter by
     * @param to the upper bound of the {@code Instant} range to filter by
     * @return a list of transcripts
     */
    @GetMapping
    public List<Transcript> findTranscripts(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        if (id != null && !id.isEmpty()) {
            return transcriptService.findTranscript(id).map(List::of).orElse(List.of());
        }
        return transcriptService.filterTranscripts(categories, account, from, to);
    }
}
