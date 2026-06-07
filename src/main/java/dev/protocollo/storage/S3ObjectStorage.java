package dev.protocollo.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Implementazione di {@link DocumentStorage} basata su object storage S3
 * (AWS S3 o MinIO). Attiva nel profilo "prod".
 *
 * La chiave del file diventa la "object key" dentro il bucket configurato.
 */
@Component
@Profile("prod")
public class S3ObjectStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3Client;
    private final String bucket;

    public S3ObjectStorage(S3Client s3Client,
                           @Value("${app.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /**
     * Alla partenza si assicura che il bucket esista, creandolo se manca.
     * Comodo in locale con MinIO; su AWS in genere il bucket esiste gia.
     */
    @PostConstruct
    void creaBucketSeMancante() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket S3 '{}' gia presente", bucket);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket S3 '{}' creato", bucket);
        }
    }

    @Override
    public String salva(String chiave, byte[] contenuto, String contentType) {
        try {
            PutObjectRequest richiesta = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(chiave)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(richiesta, RequestBody.fromBytes(contenuto));
            log.debug("Oggetto caricato su S3: bucket={}, key={}", bucket, chiave);
            return chiave;
        } catch (S3Exception e) {
            throw new StorageException("Impossibile caricare su S3 l'oggetto " + chiave, e);
        }
    }

    @Override
    public byte[] leggi(String chiave) {
        try {
            GetObjectRequest richiesta = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(chiave)
                    .build();
            return s3Client.getObjectAsBytes(richiesta).asByteArray();
        } catch (S3Exception e) {
            throw new StorageException("Impossibile leggere da S3 l'oggetto " + chiave, e);
        }
    }
}
