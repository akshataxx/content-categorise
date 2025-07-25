CREATE TABLE users (
    id UUID PRIMARY KEY,
    sub VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    picture_url VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX users_sub_email_unique ON users(sub, email);