-- Aggiunge al documento il riferimento al PDF salvato sull'object storage
-- e i campi per l'allineamento con l'indice esterno (consumer Kafka).

ALTER TABLE documento
    ADD COLUMN pdf_riferimento     VARCHAR(500),
    ADD COLUMN indicizzato         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_indicizzazione TIMESTAMP(6) WITH TIME ZONE;
