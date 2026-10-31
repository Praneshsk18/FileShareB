
package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InitRequest {
	@NotBlank(message = "fileName must not be blank")
	private String fileName;

	private String contentType;

	@Positive(message = "fileSize must be greater than zero")
	private long fileSize;
}
