package dev.protocollo.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Crea il client S3 usato nel profilo "prod".
 *
 * L'endpoint e configurabile, quindi lo stesso codice funziona sia con AWS S3
 * sia con un object storage S3-compatibile come MinIO (usato in locale).
 * Per MinIO serve il "path style" (bucket nel path invece che nel sottodominio).
 */
@Configuration
@Profile("prod")
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${app.storage.s3.endpoint}") String endpoint,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.path-style:true}") boolean pathStyle) {

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyle)
                .build();
    }
}
