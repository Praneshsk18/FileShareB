package com.example.demo.cmd;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "b2.connection-test.enabled", havingValue = "true", matchIfMissing = false)
public class BackblazeConnectionTest implements CommandLineRunner {

    private final S3Client s3Client;
    private final JdbcTemplate jdbcTemplate;

    @Value("${b2.bucket-name}")
    private String bucketName;

    @Override
    public void run(String... args) {
        try {
            s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(5)
                    .build()
            );

            System.out.println("Backblaze B2 connection successful!");
            System.out.println("Bucket: " + bucketName);
            String database = jdbcTemplate.queryForObject(
                    "SELECT current_database()",
                    String.class
                );

                System.out.println("Database connection successful!");
                System.out.println("Connected database: " + database);

        } catch (S3Exception e) {
            System.err.println("Backblaze B2 request failed.");
            System.err.println("HTTP status: " + e.statusCode());
            System.err.println("Error code: " + e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            System.err.println("Backblaze B2 connection test failed.");
            System.err.println(e.getMessage());
        }
    }
}