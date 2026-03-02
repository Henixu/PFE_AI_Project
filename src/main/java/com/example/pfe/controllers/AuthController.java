package com.example.pfe.controllers;


import com.example.pfe.auth.dto.AuthResponse;
import com.example.pfe.auth.dto.LoginRequest;
import com.example.pfe.auth.dto.RegisterRequest;
import com.example.pfe.security.CustomUserDetails;
import com.example.pfe.services.AuthService;
import com.example.pfe.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        var principal = (CustomUserDetails) authentication.getPrincipal();
        var u = principal.getUser();
        return new MeResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }

    public record MeResponse(Long userId, String name, String email, Role role) {}
}