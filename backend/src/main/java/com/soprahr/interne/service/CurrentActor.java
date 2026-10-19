package com.soprahr.interne.service;

import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;

/** Compte connecté et fiche employé associée (absente pour un compte sans employé, ex. administrateur technique). */
public record CurrentActor(User user, Employee employee) {

    public Role role() {
        return user.getRole();
    }

    public boolean isAdminOrHr() {
        return role() == Role.ADMIN || role() == Role.HR;
    }

    public Long employeeId() {
        return employee == null ? null : employee.getId();
    }
}
