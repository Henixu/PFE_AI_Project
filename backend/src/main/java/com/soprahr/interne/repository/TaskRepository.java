package com.soprahr.interne.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soprahr.interne.entity.Task;
import com.soprahr.interne.entity.enums.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @EntityGraph(attributePaths = {"assignee"})
    Page<Task> findByAssigneeId(Long assigneeId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    Page<Task> findAll(Specification<Task> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    Optional<Task> findWithDetailsById(Long id);

    /** Tâches ouvertes à échéance au plus tard à {@code until} (retards compris), dont le responsable a un compte actif. */
    @Query("select t from Task t join fetch t.assignee a join fetch a.user u "
            + "where t.status in :statuses and t.dueDate is not null and t.dueDate <= :until "
            + "and a.active = true and u.enabled = true order by t.dueDate, t.id")
    List<Task> findDueForAlert(@Param("statuses") Collection<TaskStatus> statuses, @Param("until") LocalDate until);
}
