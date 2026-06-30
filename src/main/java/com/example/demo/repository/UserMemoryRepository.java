package com.example.demo.repository;

import com.example.demo.model.UserMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    Page<UserMemory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<UserMemory> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type, Pageable pageable);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND (:type IS NULL OR :type = 'all' OR m.type = :type) AND (:keyword IS NULL OR :keyword = '' OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(COALESCE(m.metadata, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(m.type) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY m.createdAt DESC")
    Page<UserMemory> searchByUserIdAndTypeAndKeyword(@Param("userId") String userId, @Param("type") String type, @Param("keyword") String keyword, Pageable pageable);

    List<UserMemory> findTop10ByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserId(String userId);

    long countByUserIdAndType(String userId, String type);

    void deleteByUserId(String userId);
}
