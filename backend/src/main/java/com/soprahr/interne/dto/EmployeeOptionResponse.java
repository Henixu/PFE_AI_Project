package com.soprahr.interne.dto;

/** Employé proposé à l'association avec un compte. */
public record EmployeeOptionResponse(Long id, String employeeNumber, String fullName, String jobTitle) {
}
