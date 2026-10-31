package com.example.demo.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class BackBlazeConfig {
	@Value("${b2.endpoint}")
	private String endPoint;
	@Value("${b2.region}")
	private String region;
	@Value("${b2.key-id}")
	private String keyId;
	@Value("${b2.application-key}")
	private String applicationKey;
	
	@Bean
	public S3Client s3Client() {
		AwsBasicCredentials credentials = AwsBasicCredentials.create(keyId, applicationKey);
		return S3Client.builder()
				.endpointOverride(URI.create(endPoint))
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(credentials))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		AwsBasicCredentials credentials = AwsBasicCredentials.create(keyId, applicationKey);
		return S3Presigner.builder()
				.endpointOverride(URI.create(endPoint))
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(credentials))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
	}
}
