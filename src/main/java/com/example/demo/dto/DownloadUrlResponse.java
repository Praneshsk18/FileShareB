package com.example.demo.dto;

import java.time.Instant;

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
public class DownloadUrlResponse {
	private String key;
	private String fileName;
	private String downloadUrl;
	private Instant expiresAt;
}
