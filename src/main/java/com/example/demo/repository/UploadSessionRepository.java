package com.example.demo.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.UploadSession;
import com.example.demo.entity.User;
import com.example.demo.enums.Status;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {

	Optional<UploadSession> findByIdAndUser(Long id, User user);

	List<UploadSession> findByStatusInAndInstantBefore(Collection<Status> statuses, Instant cutoff);
}
