package com.example.demo.repository;

import com.example.demo.model.Knowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeRepository extends JpaRepository<Knowledge, Long> {

    // 根据分类ID查询知识库
    List<Knowledge> findByCategoryId(Long categoryId);

    // 根据状态查询知识库
    List<Knowledge> findByStatus(String status);

    // 根据标题模糊查询
    List<Knowledge> findByTitleContaining(String title);

    // 根据内容模糊查询
    @Query("SELECT k FROM Knowledge k WHERE k.content LIKE %:content%")
    List<Knowledge> findByContentContaining(@Param("content") String content);

    // 根据标签名称查询知识库
    @Query("SELECT k FROM Knowledge k JOIN k.tags t WHERE t.name = :tagName")
    List<Knowledge> findByTagName(@Param("tagName") String tagName);

    // 根据分类和状态查询知识库
    List<Knowledge> findByCategoryIdAndStatus(Long categoryId, String status);

    // 按创建时间降序查询
    List<Knowledge> findAllByOrderByCreatedAtDesc();

    // 按更新时间降序查询
    List<Knowledge> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END " +
            "FROM Knowledge k WHERE LOWER(TRIM(k.title)) = LOWER(TRIM(:title)) AND LOWER(TRIM(k.content)) = LOWER(TRIM(:content))")
    boolean existsByTitleAndContentIgnoreCase(@Param("title") String title, @Param("content") String content);
}
