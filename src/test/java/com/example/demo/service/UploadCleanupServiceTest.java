package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.entity.UploadSession;
import com.example.demo.enums.Status;
import com.example.demo.repository.UploadSessionRepository;

import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class UploadCleanupServiceTest {

	@Mock
	private UploadSessionRepository uploadSessionRepository;

	@Mock
	private S3Client s3Client;

	@InjectMocks
	private UploadCleanupService uploadCleanupService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(uploadCleanupService, "bucket", "test-bucket");
		ReflectionTestUtils.setField(uploadCleanupService, "maxAgeHours", 24);
	}

	@Test
	void testCleanupOrphanUploads_AbortsExpiredSessions() {
		Instant past = Instant.now().minus(25, ChronoUnit.HOURS);

		UploadSession session1 = UploadSession.builder()
				.id(1L)
				.objectKey("uploads/file1.png")
				.b2UploadId("uploadId-1")
				.status(Status.NOT_STARTED)
				.instant(past)
				.build();

		UploadSession session2 = UploadSession.builder()
				.id(2L)
				.objectKey("uploads/file2.zip")
				.b2UploadId("uploadId-2")
				.status(Status.IN_PROGRESS)
				.instant(past)
				.build();

		when(uploadSessionRepository.findByStatusInAndInstantBefore(any(), any()))
				.thenReturn(List.of(session1, session2));

		int cleaned = uploadCleanupService.cleanupOrphanUploads();

		assertEquals(2, cleaned);
		assertEquals(Status.ABORTED, session1.getStatus());
		assertEquals(Status.ABORTED, session2.getStatus());

		verify(s3Client, times(2)).abortMultipartUpload(any(java.util.function.Consumer.class));
		verify(uploadSessionRepository, times(2)).save(any(UploadSession.class));
	}

	@Test
	void testCleanupOrphanUploads_NoExpiredSessions() {
		when(uploadSessionRepository.findByStatusInAndInstantBefore(any(), any()))
				.thenReturn(List.of());

		int cleaned = uploadCleanupService.cleanupOrphanUploads();

		assertEquals(0, cleaned);
		verify(s3Client, never()).abortMultipartUpload(any(java.util.function.Consumer.class));
		verify(uploadSessionRepository, never()).save(any(UploadSession.class));
	}
}
