package com.soprahr.interne.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.soprahr.interne.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Charge département et manager en une requête (évite le N+1 des listes). */
    @EntityGraph(attributePaths = {"department", "manager"})
    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    @EntityGraph(attributePaths = {"department"})
    Optional<Employee> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"department", "manager", "user"})
    Optional<Employee> findWithDetailsById(Long id);
}
