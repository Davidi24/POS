-- Changing one's own email from Profile (user, 2026-11-18): the new address waits on its confirmation link and replaces
-- the old one only when the link is used. Plain confirmation links (new staff) leave it empty.
ALTER TABLE auth_email_verification_tokens
    ADD COLUMN new_email VARCHAR(150);
