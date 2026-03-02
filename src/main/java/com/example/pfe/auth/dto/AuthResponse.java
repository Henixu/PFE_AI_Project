package com.example.pfe.auth.dto;


import com.example.pfe.user.Role;

public class AuthResponse {
    public String token;
    public Long userId;
    public String name;
    public String email;
    public Role role;

    public AuthResponse(String token, Long userId, String name, String email, Role role) {
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
    }
}