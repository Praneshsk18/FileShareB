package com.example.demo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.User;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

	@EntityGraph(attributePaths = {"user"})
	Page<FileMetadata> findAllByDeletedFalse(Pageable pageable);

	@EntityGraph(attributePaths = {"user"})
	Page<FileMetadata> findAllByUserAndDeletedFalse(User user, Pageable pageable);

	Optional<FileMetadata> findByIdAndDeletedFalse(Long id);

	Optional<FileMetadata> findByIdAndUserAndDeletedFalse(Long id, User user);

	Optional<FileMetadata> findByStorageKey(String storageKey);

	@EntityGraph(attributePaths = {"user"})
	Page<FileMetadata> findByUserAndFileNameContainingIgnoreCaseAndDeletedFalse(User user, String fileName, Pageable pageable);

	@EntityGraph(attributePaths = {"user"})
	Page<FileMetadata> findByFileNameContainingIgnoreCaseAndDeletedFalse(String fileName, Pageable pageable);
}
