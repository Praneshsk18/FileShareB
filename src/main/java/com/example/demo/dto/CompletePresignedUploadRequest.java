package com.example.demo.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
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
public class CompletePresignedUploadRequest {

	@NotEmpty(message = "parts list must not be empty")
	private List<PartDto> parts;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PartDto {
		private int partNumber;
		private String eTag;
	}
}
