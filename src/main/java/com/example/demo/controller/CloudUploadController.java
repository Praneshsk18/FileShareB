package com.example.demo.controller;

import java.io.InputStream;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.CompletePresignedUploadRequest;
import com.example.demo.dto.DownloadUrlResponse;
import com.example.demo.dto.FileItemResponse;
import com.example.demo.dto.FileListResponse;
import com.example.demo.dto.FileMetadataResponse;
import com.example.demo.dto.InitRequest;
import com.example.demo.dto.InitResponse;
import com.example.demo.dto.PartPresignResponse;
import com.example.demo.dto.UploadCompleteResponse;
import com.example.demo.dto.UploadStatusResponse;
import com.example.demo.entity.User;
import com.example.demo.service.CloudUploadService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class CloudUploadController {
	private final CloudUploadService cloudUploadService;

	@PostMapping("/init")
	public ResponseEntity<InitResponse> init(
			@Valid @RequestBody InitRequest request,
			@AuthenticationPrincipal User user) {
		InitResponse response = cloudUploadService.init(request.getFileName(), request.getContentType(), request.getFileSize(), user);
		return ResponseEntity.created(null).body(response);
	}

	@PutMapping("/chunk/{id}")
	public ResponseEntity<UploadStatusResponse> uploadPart(
			@PathVariable Long id,
			@RequestHeader("Upload-Offset") long offset,
			@RequestHeader(HttpHeaders.CONTENT_LENGTH) long contentLength,
			InputStream body,
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(cloudUploadService.uploadPart(id, offset, body, contentLength, user));
	}

	@GetMapping("/{id}")
	public ResponseEntity<UploadStatusResponse> status(
			@PathVariable Long id,
			@AuthenticationPrincipal User user) {
		UploadStatusResponse response = cloudUploadService.status(id, user);
		return ResponseEntity.ok(response);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<UploadStatusResponse> abort(
			@PathVariable Long id,
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(cloudUploadService.abort(id, user));
	}

	@PostMapping("/{id}/complete")
	public ResponseEntity<UploadCompleteResponse> complete(
			@PathVariable Long id,
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(cloudUploadService.complete(id, user));
	}

	// Direct-to-S3: Get Presigned URL for uploading a specific chunk directly to Backblaze B2/S3
	@GetMapping("/{id}/part-url")
	public ResponseEntity<PartPresignResponse> getPresignedPartUrl(
			@PathVariable Long id,
			@RequestParam int partNumber,
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(cloudUploadService.getPresignedPartUrl(id, partNumber, user));
	}

	// Direct-to-S3: Complete multipart upload after client uploaded parts directly to S3
	@PostMapping("/{id}/complete-presigned")
	public ResponseEntity<UploadCompleteResponse> completeDirectUpload(
			@PathVariable Long id,
			@Valid @RequestBody CompletePresignedUploadRequest request,
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(cloudUploadService.completeDirectUpload(id, request, user));
	}

	// S3 bucket direct listing (raw objects in bucket)
	@GetMapping("/files")
	public ResponseEntity<FileListResponse> listFiles(
	        @RequestParam(required = false) String token,
	        @RequestParam(defaultValue = "50") int limit) {
	    return ResponseEntity.ok(cloudUploadService.listFiles(token, limit));
	}

	// SQL-backed file catalog listing with pagination & search scoped to authenticated user
	@GetMapping("/files/db")
	public ResponseEntity<Page<FileMetadataResponse>> listFilesFromDb(
	        @RequestParam(required = false) String search,
	        @RequestParam(defaultValue = "0") int page,
	        @RequestParam(defaultValue = "20") int size,
	        @AuthenticationPrincipal User user) {
	    return ResponseEntity.ok(cloudUploadService.listFilesFromDb(search, page, size, user));
	}

	// Presigned download URL by Database File ID (user-isolated)
	@GetMapping("/files/{id}/download")
	public ResponseEntity<DownloadUrlResponse> getDownloadUrlById(
	        @PathVariable Long id,
	        @RequestParam(defaultValue = "15") int expiryMinutes,
	        @AuthenticationPrincipal User user) {
	    return ResponseEntity.ok(cloudUploadService.getDownloadUrlByFileId(id, expiryMinutes, user));
	}

	// Delete file from both Backblaze S3 and SQL database (user-isolated)
	@DeleteMapping("/files/{id}")
	public ResponseEntity<Map<String, String>> deleteFile(
			@PathVariable Long id,
			@AuthenticationPrincipal User user) {
		cloudUploadService.deleteFile(id, user);
		return ResponseEntity.ok(Map.of("message", "File deleted successfully", "id", id.toString()));
	}

	// Presigned download URL by raw storage key
	@GetMapping("/download")
	public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
	        @RequestParam String key,
	        @RequestParam(defaultValue = "15") int expiryMinutes) {
	    return ResponseEntity.ok(cloudUploadService.getDownloadUrl(key, expiryMinutes));
	}
}
