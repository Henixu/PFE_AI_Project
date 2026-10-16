-- Aligne le type avec l'entité JPA (VARCHAR) ; le hash SHA-256 hexadécimal fait toujours 64 caractères.
ALTER TABLE password_reset_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
