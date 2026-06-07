-- Dati di esempio: alcuni documenti gia protocollati, cosi che la GET
-- restituisca qualcosa al primo avvio. Il proprietario corrisponde agli
-- utenti creati dal DataSeeder ('admin' e 'mrossi').

INSERT INTO documento (titolo, contenuto, stato, numero_protocollo, proprietario,
                       data_creazione, data_aggiornamento, version)
VALUES
    ('Determina di affidamento servizi cloud',
     'Affidamento del servizio di hosting per il portale dei servizi.',
     'PROTOCOLLATO', 'PRT-2026-000001', 'admin',
     NOW(), NOW(), 0),

    ('Richiesta accesso piattaforma',
     'Richiesta di abilitazione di un nuovo fornitore alla piattaforma cloud.',
     'PROTOCOLLATO', 'PRT-2026-000002', 'mrossi',
     NOW(), NOW(), 0);
