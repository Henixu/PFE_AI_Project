package com.soprahr.interne.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soprahr.interne.dto.CreateUserRequest;
import com.soprahr.interne.dto.EmployeeOptionResponse;
import com.soprahr.interne.dto.LinkEmployeeRequest;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.dto.UpdateRoleRequest;
import com.soprahr.interne.dto.UpdateStatusRequest;
import com.soprahr.interne.dto.UpdateUserRequest;
import com.soprahr.interne.dto.UserSummaryResponse;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.service.AdminUserService;

import jakarta.validation.Valid;

/** Administration des comptes : réservé au rôle ADMIN (règle d'URL + @PreAuthorize du service). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.list(search, role, enabled, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public UserSummaryResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryResponse create(@Valid @RequestBody CreateUserRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{id}")
    public UserSummaryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
            Authentication auth) {
        return service.updateEmail(id, request.email(), auth.getName());
    }

    @PatchMapping("/{id}/role")
    public UserSummaryResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request,
            Authentication auth) {
        return service.changeRole(id, request.role(), auth.getName());
    }

    @PatchMapping("/{id}/status")
    public UserSummaryResponse setStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
            Authentication auth) {
        return service.setEnabled(id, request.enabled(), auth.getName());
    }

    @PutMapping("/{id}/employee")
    public UserSummaryResponse linkEmployee(@PathVariable Long id, @Valid @RequestBody LinkEmployeeRequest request,
            Authentication auth) {
        return service.linkEmployee(id, request.employeeId(), auth.getName());
    }

    @DeleteMapping("/{id}/employee")
    public UserSummaryResponse unlinkEmployee(@PathVariable Long id, Authentication auth) {
        return service.unlinkEmployee(id, auth.getName());
    }

    @GetMapping("/linkable-employees")
    public List<EmployeeOptionResponse> linkableEmployees() {
        return service.linkableEmployees();
    }
}
