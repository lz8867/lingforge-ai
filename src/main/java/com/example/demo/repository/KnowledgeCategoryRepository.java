package com.example.demo.repository;

import com.example.demo.model.KnowledgeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategory, Long> {

    // 根据父分类ID查询子分类
    List<KnowledgeCategory> findByParentId(Long parentId);

    // 根据层级查询分类
    List<KnowledgeCategory> findByLevel(Integer level);

    // 根据分类名称查询
    KnowledgeCategory findByName(String name);

    // 按排序字段查询
    List<KnowledgeCategory> findAllByOrderBySortAsc();

    // 根据父分类ID按排序字段查询
    List<KnowledgeCategory> findByParentIdOrderBySortAsc(Long parentId);
}
