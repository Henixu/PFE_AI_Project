package com.soprahr.interne.service;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.LoginRequest;
import com.soprahr.interne.dto.LoginResponse;
import com.soprahr.interne.dto.UserProfileResponse;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.exception.InvalidCredentialsException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.UserRepository;
import com.soprahr.interne.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Hash factice : le temps de réponse est le même que le compte existe ou non. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordOk || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        user.setLastLoginAt(Instant.now());
        JwtService.IssuedToken issued = jwtService.generateToken(user.getEmail(), user.getRole());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), toProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentProfile(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);
        return toProfile(user);
    }

    private UserProfileResponse toProfile(User user) {
        Employee e = employeeRepository.findByUserId(user.getId()).orElse(null);
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                e != null ? e.getFirstName() : null,
                e != null ? e.getLastName() : null,
                e != null ? e.getJobTitle() : null,
                e != null && e.getDepartment() != null ? e.getDepartment().getName() : null);
    }
}
