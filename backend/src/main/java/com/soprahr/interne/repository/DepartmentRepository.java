package com.soprahr.interne.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soprahr.interne.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByNameIgnoreCase(String name);
}
