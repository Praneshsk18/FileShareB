package com.example.demo.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.entity.UploadSession;
import com.example.demo.enums.Status;
import com.example.demo.repository.UploadSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadCleanupService {

	private final UploadSessionRepository uploadSessionRepository;
	private final S3Client s3Client;

	@Value("${b2.bucket-name}")
	private String bucket;

	@Value("${upload.cleanup.max-age-hours:24}")
	private int maxAgeHours;

	@Scheduled(cron = "${upload.cleanup.cron:0 0 0 * * *}")
	public int cleanupOrphanUploads() {
		Instant cutoff = Instant.now().minus(Duration.ofHours(maxAgeHours));
		log.info("Running orphan upload cleanup for uncompleted uploads older than {}", cutoff);

		List<UploadSession> expiredSessions = uploadSessionRepository.findByStatusInAndInstantBefore(
				List.of(Status.NOT_STARTED, Status.IN_PROGRESS),
				cutoff
		);

		if (expiredSessions.isEmpty()) {
			log.info("No expired uncompleted upload sessions found.");
			return 0;
		}

		log.info("Found {} expired upload sessions to clean up.", expiredSessions.size());
		int cleanedCount = 0;

		for (UploadSession session : expiredSessions) {
			try {
				if (session.getB2UploadId() != null && session.getObjectKey() != null) {
					s3Client.abortMultipartUpload(b -> b
							.bucket(bucket)
							.key(session.getObjectKey())
							.uploadId(session.getB2UploadId())
					);
				}
			} catch (Exception e) {
				log.warn("Failed to abort multipart upload in S3 for key {} and uploadId {}: {}",
						session.getObjectKey(), session.getB2UploadId(), e.getMessage());
			}

			session.setStatus(Status.ABORTED);
			uploadSessionRepository.save(session);
			cleanedCount++;
		}

		log.info("Successfully cleaned up {} orphan upload sessions.", cleanedCount);
		return cleanedCount;
	}
}
