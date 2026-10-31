package com.example.demo.service;

import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.CompletePresignedUploadRequest;
import com.example.demo.dto.DownloadUrlResponse;
import com.example.demo.dto.FileItemResponse;
import com.example.demo.dto.FileListResponse;
import com.example.demo.dto.FileMetadataResponse;
import com.example.demo.dto.InitResponse;
import com.example.demo.dto.PartPresignResponse;
import com.example.demo.dto.UploadCompleteResponse;
import com.example.demo.dto.UploadStatusResponse;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.UploadSession;
import com.example.demo.entity.User;
import com.example.demo.enums.Status;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.UploadSessionRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudUploadService {
	private static final long MB = 1024L * 1024;
	private static final long B2_MIN_PART_SIZE = 5 * MB;
	private static final long B2_MAX_PART_SIZE = 5 * 1024 * MB;
	private static final int MAX_PARTS = 10000;

	private final UploadSessionRepository uploadSessionRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	@Value("${b2.bucket-name}")
	private String bucket;

	@Transactional
	public InitResponse init(String fileName, String contentType, long fileSize, User user) {
		if (fileSize <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
		}
		int partSize = calculatePartSize(fileSize);
		int totalParts = (int) ((fileSize + partSize - 1) / partSize);
		String key = "uploads/" + UUID.randomUUID() + "-" + sanitize(fileName);
		String type = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
		String b2UploadId = s3Client.createMultipartUpload(b -> b.bucket(bucket).key(key).contentType(type))
				.uploadId();
		UploadSession session = UploadSession.builder()
				.fileName(fileName)
				.objectKey(key)
				.b2UploadId(b2UploadId)
				.contentType(type)
				.fileSize(fileSize)
				.partSize(partSize)
				.totalParts(totalParts)
				.status(Status.NOT_STARTED)
				.user(user)
				.build();
		try {
			session = uploadSessionRepository.save(session);
			return InitResponse.builder().status(session.getStatus()).partSize(session.getPartSize())
					.totalParts(session.getTotalParts()).id(session.getId()).build();
		} catch (RuntimeException e) {
			s3Client.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(b2UploadId));
			throw e;
		}
	}

	public UploadStatusResponse uploadPart(Long id, long offset, InputStream body, long contentLength, User user) {
		UploadSession session = getActive(id, user);
		if (offset != session.getUploadedBytes()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected offset " + session.getUploadedBytes() + ", got " + offset);
		}
		int partNumber = (int) (offset / session.getPartSize()) + 1;
		long expected = expectedSize(partNumber, session.getTotalParts(), session.getPartSize(), session.getFileSize());
		if (contentLength != expected) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk must be " + expected + " bytes, got " + contentLength);
		}
		s3Client.uploadPart(UploadPartRequest.builder().bucket(bucket).key(session.getObjectKey()).uploadId(session.getB2UploadId()).partNumber(partNumber).contentLength(contentLength).build(), RequestBody.fromInputStream(body, contentLength));
		session.setUploadedBytes(offset + contentLength);
		session.setStatus(Status.IN_PROGRESS);
		session = uploadSessionRepository.save(session);
		return UploadStatusResponse.builder().id(session.getId()).partSize(session.getPartSize()).status(session.getStatus()).totalBytes(session.getFileSize()).uploadedBytes(session.getUploadedBytes()).build();
	}
	
	@Transactional
	public UploadCompleteResponse complete(Long id, User user) {
		UploadSession session = findSession(id, user);
		if (session.getStatus() == Status.COMPLETED) {
			return UploadCompleteResponse.builder().id(session.getId()).status(session.getStatus()).totalBytes(session.getFileSize()).build();
		}
		if (!isOpen(session)) {
		    throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is " + session.getStatus());
		}
		if (session.getUploadedBytes() != session.getFileSize()) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT,
	                "Upload incomplete: " + session.getUploadedBytes() + " of " + session.getFileSize() + " bytes");
	    }
		List<Part> parts = listParts(session);
		long storedBytes = parts.stream().mapToLong(Part::size).sum();
		if (parts.size() != session.getTotalParts() || storedBytes != session.getFileSize()) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT,
	                "Storage has " + parts.size() + " parts / " + storedBytes + " bytes, expected "
	                        + session.getTotalParts() + " parts / " + session.getFileSize() + " bytes");
	    }
		List<CompletedPart> completedParts = parts.stream().sorted(Comparator.comparingInt(Part::partNumber)).map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build()).toList();
		s3Client.completeMultipartUpload(b -> b.bucket(bucket).key(session.getObjectKey()).uploadId(session.getB2UploadId()).multipartUpload(m -> m.parts(completedParts)));
		session.setStatus(Status.COMPLETED);
		UploadSession saved = uploadSessionRepository.save(session);

		// Record in FileMetadata table for fast SQL queries and display
		String effectiveFileName = session.getFileName() != null ? session.getFileName() : displayName(session.getObjectKey());
		FileMetadata fileMetadata = FileMetadata.builder()
				.fileName(effectiveFileName)
				.storageKey(session.getObjectKey())
				.contentType(session.getContentType())
				.fileSize(session.getFileSize())
				.createdAt(Instant.now())
				.deleted(false)
				.user(session.getUser())
				.build();
		fileMetadataRepository.save(fileMetadata);

		return UploadCompleteResponse.builder().id(id).status(Status.COMPLETED).totalBytes(saved.getFileSize()).build();
	}

	public PartPresignResponse getPresignedPartUrl(Long id, int partNumber, User user) {
		UploadSession session = getActive(id, user);

		if (partNumber < 1 || partNumber > session.getTotalParts()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Part number must be between 1 and " + session.getTotalParts());
		}

		long expectedSize = expectedSize(partNumber, session.getTotalParts(), session.getPartSize(), session.getFileSize());

		UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
				.bucket(bucket)
				.key(session.getObjectKey())
				.uploadId(session.getB2UploadId())
				.partNumber(partNumber)
				.build();

		UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
				.signatureDuration(Duration.ofMinutes(15))
				.uploadPartRequest(uploadPartRequest)
				.build();

		PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(presignRequest);

		return PartPresignResponse.builder()
				.sessionId(session.getId())
				.partNumber(partNumber)
				.expectedPartSize(expectedSize)
				.uploadUrl(presigned.url().toString())
				.expiresAt(presigned.expiration())
				.build();
	}

	@Transactional
	public UploadCompleteResponse completeDirectUpload(Long id, CompletePresignedUploadRequest request, User user) {
		UploadSession session = findSession(id, user);
		if (session.getStatus() == Status.COMPLETED) {
			return UploadCompleteResponse.builder().id(session.getId()).status(session.getStatus()).totalBytes(session.getFileSize()).build();
		}
		if (!isOpen(session)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is " + session.getStatus());
		}

		if (request.getParts() == null || request.getParts().size() != session.getTotalParts()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Expected " + session.getTotalParts() + " parts, got " + (request.getParts() == null ? 0 : request.getParts().size()));
		}

		List<CompletedPart> completedParts = request.getParts().stream()
				.sorted(Comparator.comparingInt(CompletePresignedUploadRequest.PartDto::getPartNumber))
				.map(p -> CompletedPart.builder().partNumber(p.getPartNumber()).eTag(p.getETag()).build())
				.toList();

		s3Client.completeMultipartUpload(b -> b
				.bucket(bucket)
				.key(session.getObjectKey())
				.uploadId(session.getB2UploadId())
				.multipartUpload(m -> m.parts(completedParts)));

		session.setUploadedBytes(session.getFileSize());
		session.setStatus(Status.COMPLETED);
		UploadSession saved = uploadSessionRepository.save(session);

		String effectiveFileName = session.getFileName() != null ? session.getFileName() : displayName(session.getObjectKey());
		FileMetadata fileMetadata = FileMetadata.builder()
				.fileName(effectiveFileName)
				.storageKey(session.getObjectKey())
				.contentType(session.getContentType())
				.fileSize(session.getFileSize())
				.createdAt(Instant.now())
				.deleted(false)
				.user(session.getUser())
				.build();
		fileMetadataRepository.save(fileMetadata);

		return UploadCompleteResponse.builder().id(id).status(Status.COMPLETED).totalBytes(saved.getFileSize()).build();
	}

	public UploadStatusResponse status(Long id, User user) {
		UploadSession session = findSession(id, user);
		return UploadStatusResponse.builder().id(session.getId()).status(session.getStatus()).uploadedBytes(session.getUploadedBytes()).totalBytes(session.getFileSize()).partSize(session.getPartSize()).build();
	}

	public UploadStatusResponse abort(Long id, User user) {
		UploadSession session = findSession(id, user);
		if (session.getStatus() == Status.COMPLETED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot abort a completed upload");
		}
		if (session.getStatus() == Status.ABORTED) {
			return UploadStatusResponse.builder().id(session.getId()).status(session.getStatus()).uploadedBytes(session.getUploadedBytes()).totalBytes(session.getFileSize()).partSize(session.getPartSize()).build();
		}
		try {
			s3Client.abortMultipartUpload(b -> b.bucket(bucket).key(session.getObjectKey()).uploadId(session.getB2UploadId()));
		} catch (Exception e) {
			// Log and continue to mark aborted in database
		}
		session.setStatus(Status.ABORTED);
		UploadSession saved = uploadSessionRepository.save(session);
		return UploadStatusResponse.builder().id(saved.getId()).status(saved.getStatus()).uploadedBytes(saved.getUploadedBytes()).totalBytes(saved.getFileSize()).partSize(saved.getPartSize()).build();
	}
	
	public FileListResponse listFiles(String token, int limit) {
		int max = Math.min(Math.max(limit, 1), 200);
		ListObjectsV2Request.Builder req = ListObjectsV2Request.builder().bucket(bucket).maxKeys(max);
		if (StringUtils.hasText(token)) {
			req.continuationToken(token);
		}
		ListObjectsV2Response res = s3Client.listObjectsV2(req.build());
		List<FileItemResponse> files = res.contents().stream().filter(o -> !o.key().endsWith("/")).map(o -> new FileItemResponse(o.key(), displayName(o.key()), o.size(), o.lastModified())).toList();
		return new FileListResponse(files, res.isTruncated() ? res.nextContinuationToken() : null);
	}

	@Transactional
	public Page<FileMetadataResponse> listFilesFromDb(String search, int page, int size, User user) {
		int validatedPage = Math.max(page, 0);
		int validatedSize = Math.min(Math.max(size, 1), 100);
		Pageable pageable = PageRequest.of(validatedPage, validatedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<FileMetadata> results;
		if (user != null) {
			if (StringUtils.hasText(search)) {
				results = fileMetadataRepository.findByUserAndFileNameContainingIgnoreCaseAndDeletedFalse(user, search.trim(), pageable);
			} else {
				results = fileMetadataRepository.findAllByUserAndDeletedFalse(user, pageable);
			}
		} else {
			if (StringUtils.hasText(search)) {
				results = fileMetadataRepository.findByFileNameContainingIgnoreCaseAndDeletedFalse(search.trim(), pageable);
			} else {
				results = fileMetadataRepository.findAllByDeletedFalse(pageable);
			}
		}
		return results.map(FileMetadataResponse::fromEntity);
	}

	@Transactional
	public void deleteFile(Long id, User user) {
		FileMetadata metadata;
		if (user != null) {
			metadata = fileMetadataRepository.findByIdAndUserAndDeletedFalse(id, user)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found or access denied"));
		} else {
			metadata = fileMetadataRepository.findByIdAndDeletedFalse(id)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
		}

		log.info("Deleting file id: {}, key: '{}' from S3 bucket: '{}'", id, metadata.getStorageKey(), bucket);
		try {
			software.amazon.awssdk.services.s3.model.DeleteObjectResponse delRes = s3Client.deleteObject(
					DeleteObjectRequest.builder().bucket(bucket).key(metadata.getStorageKey()).build());
			log.info("S3 deleteObject called successfully for key: '{}', response: {}", 
					metadata.getStorageKey(), delRes != null ? delRes.toString() : "null");
		} catch (Exception e) {
			log.error("Failed to delete object from S3: {}", e.getMessage(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file from storage: " + e.getMessage());
		}

		metadata.setDeleted(true);
		fileMetadataRepository.save(metadata);
	}

	public DownloadUrlResponse getDownloadUrlByFileId(Long id, int expiryMinutes, User user) {
		FileMetadata metadata;
		if (user != null) {
			metadata = fileMetadataRepository.findByIdAndUserAndDeletedFalse(id, user)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found or access denied"));
		} else {
			metadata = fileMetadataRepository.findByIdAndDeletedFalse(id)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
		}
		return getDownloadUrl(metadata.getStorageKey(), metadata.getFileName(), expiryMinutes);
	}
	
	public String displayName(String key) {
	    String name = key.substring(key.lastIndexOf('/') + 1);
	    return name.replaceFirst("^[0-9a-fA-F-]{36}-", "");
	}

	public DownloadUrlResponse getDownloadUrl(String key, int expiryMinutes) {
		return getDownloadUrl(key, displayName(key), expiryMinutes);
	}

	public DownloadUrlResponse getDownloadUrl(String key, String originalFileName, int expiryMinutes) {
		if (!StringUtils.hasText(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Object key must not be empty");
		}
		int validMinutes = Math.min(Math.max(expiryMinutes, 1), 1440);
		Duration duration = Duration.ofMinutes(validMinutes);

		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.responseContentDisposition("attachment; filename=\"" + (StringUtils.hasText(originalFileName) ? originalFileName : displayName(key)) + "\"")
				.build();

		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(duration)
				.getObjectRequest(getObjectRequest)
				.build();

		PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);

		return DownloadUrlResponse.builder()
				.key(key)
				.fileName(StringUtils.hasText(originalFileName) ? originalFileName : displayName(key))
				.downloadUrl(presigned.url().toString())
				.expiresAt(presigned.expiration())
				.build();
	}

	public int calculatePartSize(long fileSize) {
		long neededSize = (fileSize + MAX_PARTS - 1) / MAX_PARTS;
		int partSize = (int) Math.max(B2_MIN_PART_SIZE, neededSize);
		if (partSize > B2_MAX_PART_SIZE) {
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "File too large");
		}
		return partSize;
	}

	public String sanitize(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return "file";
		}
		String name = Paths.get(fileName).getFileName().toString();
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private UploadSession getActive(Long id, User user) {
		UploadSession session = findSession(id, user);
		if (!isOpen(session)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is " + session.getStatus());
		}
		return session;
	}

	public long expectedSize(int partNumber, int totalParts, int partSize, long fileSize) {
		if (partNumber < totalParts) {
			return partSize;
		}
		return fileSize - (long) partSize * (totalParts - 1);
	}

	private UploadSession findSession(Long id, User user) {
		if (user != null) {
			return uploadSessionRepository.findByIdAndUser(id, user)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found or access denied"));
		}
		return uploadSessionRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));
	}

	private boolean isOpen(UploadSession s) {
	    return s.getStatus() == Status.NOT_STARTED
	            || s.getStatus() == Status.IN_PROGRESS;
	}

	private List<Part> listParts(UploadSession s) {
	    List<Part> all = new ArrayList<>();
	    s3Client.listPartsPaginator(b -> b
	                    .bucket(bucket)
	                    .key(s.getObjectKey())
	                    .uploadId(s.getB2UploadId()))
	            .forEach(page -> all.addAll(page.parts()));
	    return all;
	}
}
