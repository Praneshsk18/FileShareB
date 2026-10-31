package com.example.demo.dto;

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
public class AuthResponse {
	private String token;
	@Builder.Default
	private String tokenType = "Bearer";
	private Long userId;
	private String username;
	private String email;
	private String role;
}
