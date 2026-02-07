-- Add notes column to user_transcripts for user-specific notes
ALTER TABLE user_transcripts ADD COLUMN notes TEXT DEFAULT NULL;
