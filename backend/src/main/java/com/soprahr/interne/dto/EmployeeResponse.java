package com.soprahr.interne.dto;

import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String employeeNumber,
        String firstName,
        String lastName,
        String jobTitle,
        String phone,
        LocalDate hireDate,
        boolean active,
        Long departmentId,
        String departmentName,
        Long managerId,
        String managerName) {
}