package com.example.demo.dto;

import java.time.Instant;

import com.example.demo.entity.FileMetadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataResponse {
	private Long id;
	private String fileName;
	private String storageKey;
	private String contentType;
	private long fileSize;
	private Instant createdAt;
	private String uploadedBy;

	public static FileMetadataResponse fromEntity(FileMetadata entity) {
		return FileMetadataResponse.builder()
				.id(entity.getId())
				.fileName(entity.getFileName())
				.storageKey(entity.getStorageKey())
				.contentType(entity.getContentType())
				.fileSize(entity.getFileSize())
				.createdAt(entity.getCreatedAt())
				.uploadedBy(entity.getUser() != null ? entity.getUser().getUsername() : null)
				.build();
	}
}
