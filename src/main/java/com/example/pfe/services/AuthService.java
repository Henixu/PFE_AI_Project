package com.example.pfe.services;


import com.example.pfe.auth.dto.AuthResponse;
import com.example.pfe.auth.dto.LoginRequest;
import com.example.pfe.auth.dto.RegisterRequest;
import com.example.pfe.repository.UserRepository;
import com.example.pfe.security.JwtService;
import com.example.pfe.user.Role;
import com.example.pfe.user.User;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email.toLowerCase())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setName(req.name);
        user.setEmail(req.email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password));
        user.setRole(req.role == null ? Role.STUDENT : req.role);

        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email.toLowerCase(), req.password)
        );

        User user = userRepository.findByEmail(req.email.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid login"));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}