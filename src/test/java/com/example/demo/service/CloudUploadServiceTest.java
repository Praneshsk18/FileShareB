package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.CompletePresignedUploadRequest;
import com.example.demo.dto.FileMetadataResponse;
import com.example.demo.dto.InitResponse;
import com.example.demo.dto.PartPresignResponse;
import com.example.demo.dto.UploadCompleteResponse;
import com.example.demo.dto.UploadStatusResponse;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.UploadSession;
import com.example.demo.enums.Status;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.UploadSessionRepository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@ExtendWith(MockitoExtension.class)
class CloudUploadServiceTest {

	@Mock
	private UploadSessionRepository uploadSessionRepository;

	@Mock
	private FileMetadataRepository fileMetadataRepository;

	@Mock
	private S3Client s3Client;

	@Mock
	private S3Presigner s3Presigner;

	@InjectMocks
	private CloudUploadService cloudUploadService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(cloudUploadService, "bucket", "test-bucket");
	}

	@Test
	void testCalculatePartSize_SmallFile() {
		long fileSize = 10 * 1024 * 1024; // 10MB
		int partSize = cloudUploadService.calculatePartSize(fileSize);
		assertEquals(5 * 1024 * 1024, partSize); // default min 5MB
	}

	@Test
	void testSanitizeFileName() {
		String sanitized = cloudUploadService.sanitize("my test file @#$.pdf");
		assertEquals("my_test_file____.pdf", sanitized);
	}

	@Test
	void testExpectedSize() {
		int totalParts = 3;
		int partSize = 5 * 1024 * 1024;
		long fileSize = 12 * 1024 * 1024; // Part 1: 5MB, Part 2: 5MB, Part 3: 2MB

		assertEquals(5 * 1024 * 1024, cloudUploadService.expectedSize(1, totalParts, partSize, fileSize));
		assertEquals(5 * 1024 * 1024, cloudUploadService.expectedSize(2, totalParts, partSize, fileSize));
		assertEquals(2 * 1024 * 1024, cloudUploadService.expectedSize(3, totalParts, partSize, fileSize));
	}

	@Test
	void testInit_Success() {
		CreateMultipartUploadResponse s3Res = CreateMultipartUploadResponse.builder()
				.uploadId("s3-upload-123")
				.build();
		when(s3Client.createMultipartUpload(any(java.util.function.Consumer.class))).thenReturn(s3Res);

		UploadSession savedSession = UploadSession.builder()
				.id(1L)
				.fileName("test.png")
				.objectKey("uploads/test.png")
				.b2UploadId("s3-upload-123")
				.contentType("image/png")
				.fileSize(10 * 1024 * 1024)
				.partSize(5 * 1024 * 1024)
				.totalParts(2)
				.status(Status.NOT_STARTED)
				.build();

		when(uploadSessionRepository.save(any(UploadSession.class))).thenReturn(savedSession);

		InitResponse response = cloudUploadService.init("test.png", "image/png", 10 * 1024 * 1024, null);

		assertNotNull(response);
		assertEquals(1L, response.getId());
		assertEquals(Status.NOT_STARTED, response.getStatus());
		assertEquals(2, response.getTotalParts());
	}

	@Test
	void testUploadPart_SuccessiveChunksAllowed() {
		long partSize = 5 * 1024 * 1024;
		long fileSize = 10 * 1024 * 1024;

		UploadSession session = UploadSession.builder()
				.id(1L)
				.fileName("test.bin")
				.objectKey("uploads/test.bin")
				.b2UploadId("up-123")
				.contentType("application/octet-stream")
				.fileSize(fileSize)
				.partSize((int) partSize)
				.totalParts(2)
				.uploadedBytes(partSize)
				.status(Status.IN_PROGRESS) // Already IN_PROGRESS from part 1
				.build();

		when(uploadSessionRepository.findById(1L)).thenReturn(Optional.of(session));
		when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(i -> i.getArgument(0));
		when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
				.thenReturn(UploadPartResponse.builder().build());

		byte[] chunk = new byte[(int) partSize];
		UploadStatusResponse res = cloudUploadService.uploadPart(1L, partSize, new ByteArrayInputStream(chunk), partSize, null);

		assertNotNull(res);
		assertEquals(fileSize, res.getUploadedBytes());
		assertEquals(Status.IN_PROGRESS, res.getStatus());
	}

	@Test
	void testUploadPart_WrongOffset_ThrowsConflict() {
		UploadSession session = UploadSession.builder()
				.id(1L)
				.objectKey("uploads/test.bin")
				.b2UploadId("up-123")
				.fileSize(10 * 1024 * 1024)
				.partSize(5 * 1024 * 1024)
				.totalParts(2)
				.uploadedBytes(0)
				.status(Status.NOT_STARTED)
				.build();

		when(uploadSessionRepository.findById(1L)).thenReturn(Optional.of(session));

		assertThrows(ResponseStatusException.class, () ->
				cloudUploadService.uploadPart(1L, 1000, new ByteArrayInputStream(new byte[10]), 10, null)
		);
	}

	@Test
	void testAbort_Success() {
		UploadSession session = UploadSession.builder()
				.id(1L)
				.objectKey("uploads/test.bin")
				.b2UploadId("up-123")
				.status(Status.IN_PROGRESS)
				.build();

		when(uploadSessionRepository.findById(1L)).thenReturn(Optional.of(session));
		when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(i -> i.getArgument(0));

		UploadStatusResponse response = cloudUploadService.abort(1L, null);

		assertEquals(Status.ABORTED, response.getStatus());
		verify(s3Client).abortMultipartUpload(any(java.util.function.Consumer.class));
	}

	@Test
	void testListFilesFromDb_Success() {
		FileMetadata file = FileMetadata.builder()
				.id(10L)
				.fileName("presentation.pdf")
				.storageKey("uploads/pres-123.pdf")
				.fileSize(2048)
				.contentType("application/pdf")
				.createdAt(Instant.now())
				.deleted(false)
				.build();

		Page<FileMetadata> page = new PageImpl<>(List.of(file));
		when(fileMetadataRepository.findAllByDeletedFalse(any(Pageable.class))).thenReturn(page);

		Page<FileMetadataResponse> result = cloudUploadService.listFilesFromDb(null, 0, 10, null);

		assertEquals(1, result.getTotalElements());
		assertEquals("presentation.pdf", result.getContent().get(0).getFileName());
	}

	@Test
	void testDeleteFile_Success() {
		FileMetadata file = FileMetadata.builder()
				.id(5L)
				.fileName("doc.txt")
				.storageKey("uploads/doc-5.txt")
				.fileSize(100)
				.deleted(false)
				.build();

		when(fileMetadataRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(file));

		cloudUploadService.deleteFile(5L, null);

		verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
		verify(fileMetadataRepository).save(file);
		assertTrue(file.isDeleted());
	}

	@Test
	void testGetPresignedPartUrl_Success() throws Exception {
		UploadSession session = UploadSession.builder()
				.id(2L)
				.objectKey("uploads/direct.bin")
				.b2UploadId("b2-direct-123")
				.fileSize(12 * 1024 * 1024)
				.partSize(5 * 1024 * 1024)
				.totalParts(3)
				.status(Status.NOT_STARTED)
				.build();

		when(uploadSessionRepository.findById(2L)).thenReturn(Optional.of(session));

		PresignedUploadPartRequest presigned = org.mockito.Mockito.mock(PresignedUploadPartRequest.class);
		when(presigned.url()).thenReturn(URI.create("https://s3.ca-east-006.backblazeb2.com/upload-part-1").toURL());
		Instant exp = Instant.now().plusSeconds(900);
		when(presigned.expiration()).thenReturn(exp);

		when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenReturn(presigned);

		PartPresignResponse response = cloudUploadService.getPresignedPartUrl(2L, 1, null);

		assertNotNull(response);
		assertEquals(2L, response.getSessionId());
		assertEquals(1, response.getPartNumber());
		assertEquals(5 * 1024 * 1024, response.getExpectedPartSize());
		assertEquals("https://s3.ca-east-006.backblazeb2.com/upload-part-1", response.getUploadUrl());
		assertEquals(exp, response.getExpiresAt());
	}

	@Test
	void testCompleteDirectUpload_Success() {
		UploadSession session = UploadSession.builder()
				.id(3L)
				.fileName("direct-file.zip")
				.objectKey("uploads/direct-file.zip")
				.b2UploadId("b2-direct-456")
				.contentType("application/zip")
				.fileSize(10 * 1024 * 1024)
				.partSize(5 * 1024 * 1024)
				.totalParts(2)
				.status(Status.NOT_STARTED)
				.build();

		when(uploadSessionRepository.findById(3L)).thenReturn(Optional.of(session));
		when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(i -> i.getArgument(0));

		CompletePresignedUploadRequest request = CompletePresignedUploadRequest.builder()
				.parts(List.of(
						new CompletePresignedUploadRequest.PartDto(1, "\"etag1\""),
						new CompletePresignedUploadRequest.PartDto(2, "\"etag2\"")
				))
				.build();

		UploadCompleteResponse completeResponse = cloudUploadService.completeDirectUpload(3L, request, null);

		assertNotNull(completeResponse);
		assertEquals(Status.COMPLETED, completeResponse.getStatus());
		assertEquals(10 * 1024 * 1024, completeResponse.getTotalBytes());
		verify(s3Client).completeMultipartUpload(any(java.util.function.Consumer.class));
		verify(fileMetadataRepository).save(any(FileMetadata.class));
	}
}
