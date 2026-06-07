-- Aggiunge l'email all'utente, usata nel documento di accreditamento.
ALTER TABLE utente
    ADD COLUMN email VARCHAR(150);
