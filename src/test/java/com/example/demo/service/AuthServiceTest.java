package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@InjectMocks
	private AuthService authService;

	@Test
	void testRegister_Success() {
		RegisterRequest req = RegisterRequest.builder()
				.username("testuser")
				.email("test@example.com")
				.password("secret123")
				.build();

		when(userRepository.existsByUsername("testuser")).thenReturn(false);
		when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
		when(passwordEncoder.encode("secret123")).thenReturn("encodedPassword");

		User saved = User.builder()
				.id(1L)
				.username("testuser")
				.email("test@example.com")
				.password("encodedPassword")
				.role(Role.ROLE_USER)
				.build();

		when(userRepository.save(any(User.class))).thenReturn(saved);
		when(jwtService.generateToken(saved)).thenReturn("mock.jwt.token");

		AuthResponse res = authService.register(req);

		assertNotNull(res);
		assertEquals("mock.jwt.token", res.getToken());
		assertEquals("testuser", res.getUsername());
		assertEquals(1L, res.getUserId());
	}

	@Test
	void testLogin_Success() {
		LoginRequest req = LoginRequest.builder()
				.username("testuser")
				.password("secret123")
				.build();

		User user = User.builder()
				.id(1L)
				.username("testuser")
				.email("test@example.com")
				.password("encodedPassword")
				.role(Role.ROLE_USER)
				.build();

		when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret123", "encodedPassword")).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("mock.jwt.token");

		AuthResponse res = authService.login(req);

		assertNotNull(res);
		assertEquals("mock.jwt.token", res.getToken());
		assertEquals("testuser", res.getUsername());
	}

	@Test
	void testLogin_BadPassword_ThrowsUnauthorized() {
		LoginRequest req = LoginRequest.builder()
				.username("testuser")
				.password("wrongpassword")
				.build();

		User user = User.builder()
				.username("testuser")
				.password("encodedPassword")
				.build();

		when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);

		assertThrows(ResponseStatusException.class, () -> authService.login(req));
	}
}
