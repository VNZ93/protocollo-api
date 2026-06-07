package dev.protocollo.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementazione di {@link DocumentStorage} che salva i file sul filesystem
 * locale. Attiva nel profilo "dev" (e quello predefinito).
 *
 * La chiave viene usata come percorso relativo dentro la cartella base
 * configurata in {@code app.storage.local.directory}.
 */
@Component
@Profile("dev")
public class LocalFileSystemStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorage.class);

    private final Path cartellaBase;

    public LocalFileSystemStorage(
            @Value("${app.storage.local.directory}") String cartellaBase) {
        this.cartellaBase = Paths.get(cartellaBase).toAbsolutePath().normalize();
        log.info("Storage locale attivo, cartella base: {}", this.cartellaBase);
    }

    @Override
    public String salva(String chiave, byte[] contenuto, String contentType) {
        try {
            Path destinazione = risolvi(chiave);
            Files.createDirectories(destinazione.getParent());
            Files.write(destinazione, contenuto);
            log.debug("File salvato su disco: {}", destinazione);
            return chiave;
        } catch (IOException e) {
            throw new StorageException("Impossibile salvare il file " + chiave, e);
        }
    }

    @Override
    public byte[] leggi(String chiave) {
        try {
            return Files.readAllBytes(risolvi(chiave));
        } catch (IOException e) {
            throw new StorageException("Impossibile leggere il file " + chiave, e);
        }
    }

    /**
     * Risolve la chiave dentro la cartella base e verifica che non "esca" da
     * essa (protezione da path traversal del tipo "../../etc/passwd").
     */
    private Path risolvi(String chiave) {
        Path risolto = cartellaBase.resolve(chiave).normalize();
        if (!risolto.startsWith(cartellaBase)) {
            throw new StorageException("Chiave non valida: " + chiave, null);
        }
        return risolto;
    }
}
