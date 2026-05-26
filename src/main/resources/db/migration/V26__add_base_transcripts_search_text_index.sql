CREATE INDEX IF NOT EXISTS idx_base_transcripts_search_text
    ON base_transcripts USING gin (
        to_tsvector(
            'english'::regconfig,
            COALESCE(title, '') || ' ' ||
            COALESCE(generated_title, '') || ' ' ||
            COALESCE(description, '') || ' ' ||
            COALESCE(structured_content::text, '') || ' ' ||
            COALESCE(transcript, '')
        )
    );
