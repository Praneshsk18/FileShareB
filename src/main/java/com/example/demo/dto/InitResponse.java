package com.example.demo.dto;

import com.example.demo.enums.Status;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class InitResponse {
	private Long id;
	private Status status;
	private int totalParts;
	private int partSize;
}
