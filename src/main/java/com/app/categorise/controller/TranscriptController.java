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
     * Endpoint to filter transcripts based on various optional parameters.
     *
     * @param id Optional transcript ID to filter by.
     * @param categories Optional list of categories to filter by.
     * @param account Optional account name to filter by.
     * @param from Optional start date for filtering transcripts uploaded after this date.
     * @param to Optional end date for filtering transcripts uploaded before this date.
     * @return A list of transcripts that match the provided filters.
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
