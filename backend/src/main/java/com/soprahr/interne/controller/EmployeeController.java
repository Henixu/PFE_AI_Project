package com.soprahr.interne.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

import com.soprahr.interne.dto.DepartmentOptionResponse;
import com.soprahr.interne.dto.EmployeeRequest;
import com.soprahr.interne.dto.EmployeeResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.service.EmployeeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EmployeeResponse> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "lastName") String sortBy, @RequestParam(defaultValue = "asc") String direction) {
        return service.list(search, departmentId, active, page, size, sortBy, direction);
    }

    @GetMapping("/departments")
    public List<DepartmentOptionResponse> departments() {
        return service.departments();
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request,
            Authentication auth) {
        return service.update(id, request, auth.getName());
    }

    @PatchMapping("/{id}/status")
    public EmployeeResponse setActive(@PathVariable Long id, @RequestParam boolean active, Authentication auth) {
        return service.setActive(id, active, auth.getName());
    }
}