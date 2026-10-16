package com.soprahr.interne.dto;

import com.soprahr.interne.entity.enums.Role;

import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull Role role) {
}
