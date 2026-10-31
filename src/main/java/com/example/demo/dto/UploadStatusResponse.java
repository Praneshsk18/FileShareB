package com.example.demo.dto;

import com.example.demo.enums.Status;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class UploadStatusResponse {
	private Long id;
	private Status status;
	private long uploadedBytes;
	private long totalBytes;
	private long partSize;
}
