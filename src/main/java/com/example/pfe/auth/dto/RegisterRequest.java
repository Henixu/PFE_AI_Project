package com.example.pfe.auth.dto;

import com.example.pfe.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegisterRequest {
    @NotBlank
    public String name;
    @Email
    @NotBlank public String email;
    @NotBlank public String password;

    // For MVP you can allow role selection.
    // In production, you usually restrict TEACHER/ADMIN creation.
    @NotNull
    public Role role;
}
