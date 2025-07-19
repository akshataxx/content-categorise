-- new table for pending links
CREATE TABLE untranscribed_links (
    user_id UUID NOT NULL,
    link TEXT NOT NULL,
    PRIMARY KEY(user_id, link)
);