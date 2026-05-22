CREATE INDEX IF NOT EXISTS idx_base_transcripts_search_text
    ON base_transcripts USING gin (
        to_tsvector(
            'english',
            concat_ws(
                ' ',
                title,
                generated_title,
                description,
                structured_content::text,
                transcript
            )
        )
    );
