-- Introduce il workflow BOZZA -> APPROVATA -> PROTOCOLLATO (con approvazione
-- di un amministratore e protocollazione automatica successiva) e trasforma
-- l'archiviazione da stato a tag indipendente.

ALTER TABLE documento ADD COLUMN archiviato BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE documento ADD COLUMN data_approvazione TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE documento ADD COLUMN protocollazione_in_coda BOOLEAN NOT NULL DEFAULT FALSE;

-- Migrazione defensiva: lo stato ARCHIVIATO non esiste piu, i documenti che
-- lo avevano diventano PROTOCOLLATO con il nuovo tag archiviato a TRUE.
UPDATE documento SET archiviato = TRUE, stato = 'PROTOCOLLATO' WHERE stato = 'ARCHIVIATO';

CREATE INDEX idx_documento_archiviato ON documento (archiviato);
