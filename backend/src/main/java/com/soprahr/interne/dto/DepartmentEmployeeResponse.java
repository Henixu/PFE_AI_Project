package com.soprahr.interne.dto;

public record DepartmentEmployeeResponse(Long id, String employeeNumber, String fullName, String jobTitle,
        boolean active) {
}
