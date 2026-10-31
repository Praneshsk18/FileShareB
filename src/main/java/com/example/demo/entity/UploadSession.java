package com.example.demo.entity;

import java.time.Instant;

import com.example.demo.enums.Status;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadSession {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String fileName;
	private String objectKey;
	private String b2UploadId;
	private String contentType;
	private long fileSize;
	private int partSize;
	private int totalParts;
	@Builder.Default
	private long uploadedBytes = 0;
	@Builder.Default
	private Status status = Status.NOT_STARTED;
	@Builder.Default
	private Instant instant = Instant.now();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;
}
