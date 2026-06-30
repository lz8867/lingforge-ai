package com.example.demo.repository;

import com.example.demo.model.KnowledgeTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTagRepository extends JpaRepository<KnowledgeTag, Long> {

    // 根据标签名称查询
    KnowledgeTag findByName(String name);

    // 根据标签名称模糊查询
    java.util.List<KnowledgeTag> findByNameContaining(String name);
}
