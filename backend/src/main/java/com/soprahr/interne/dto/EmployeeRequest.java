package com.soprahr.interne.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record EmployeeRequest(
        @NotBlank @Size(max = 20) String employeeNumber,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 150) String jobTitle,
        @Size(max = 30) String phone,
        @PastOrPresent LocalDate hireDate,
        Long departmentId,
        Long managerId) {
}