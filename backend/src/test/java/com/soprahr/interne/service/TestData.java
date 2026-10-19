package com.soprahr.interne.service;

import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.entity.Department;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;

/** Fabriques d'entités avec identifiant pour les tests unitaires de services (sans base de données). */
final class TestData {

    private TestData() {
    }

    static User user(long id, String email, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setPasswordHash("x");
        user.setRole(role);
        return user;
    }

    static Employee employee(long id, String firstName, String lastName, User user, Employee manager) {
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", id);
        employee.setEmployeeNumber("EMP-" + id);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setUser(user);
        employee.setManager(manager);
        return employee;
    }

    static Department department(long id, String name) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", id);
        department.setName(name);
        return department;
    }
}
