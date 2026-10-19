package com.soprahr.interne.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.entity.User;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.UserRepository;

/** Retrouve, depuis l'e-mail du jeton, le compte et l'employé sur lesquels s'appliquent les règles d'accès. */
@Service
@Transactional(readOnly = true)
public class ActorResolver {

    private final UserRepository users;
    private final EmployeeRepository employees;

    public ActorResolver(UserRepository users, EmployeeRepository employees) {
        this.users = users;
        this.employees = employees;
    }

    public CurrentActor resolve(String email) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable"));
        return new CurrentActor(user, employees.findByUserId(user.getId()).orElse(null));
    }
}
