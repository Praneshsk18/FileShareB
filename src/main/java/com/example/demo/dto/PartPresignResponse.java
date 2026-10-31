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
public class PartPresignResponse {
	private Long sessionId;
	private int partNumber;
	private long expectedPartSize;
	private String uploadUrl;
	private Instant expiresAt;
}
