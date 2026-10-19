package com.soprahr.interne.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.soprahr.interne.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @EntityGraph(attributePaths = {"assignee"})
    Page<Task> findByAssigneeId(Long assigneeId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    Page<Task> findAll(Specification<Task> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    Optional<Task> findWithDetailsById(Long id);
}
