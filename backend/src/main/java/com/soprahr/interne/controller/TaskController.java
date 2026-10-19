package com.soprahr.interne.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.soprahr.interne.dto.EmployeeOptionResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.dto.TaskRequest;
import com.soprahr.interne.dto.TaskResponse;
import com.soprahr.interne.dto.TaskStatusRequest;
import com.soprahr.interne.entity.enums.TaskPriority;
import com.soprahr.interne.entity.enums.TaskStatus;
import com.soprahr.interne.service.TaskService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TaskResponse> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long assigneeId, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) TaskStatus status, @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "dueDate") String sortBy, @RequestParam(defaultValue = "asc") String direction,
            Authentication auth) {
        return service.list(new TaskService.Filter(search, assigneeId, priority, status, overdue, mine, dueFrom, dueTo),
                page, size, sortBy, direction, auth.getName());
    }

    /** Employés auxquels l'utilisateur connecté peut affecter une tâche. */
    @GetMapping("/assignees")
    public List<EmployeeOptionResponse> assignees(Authentication auth) {
        return service.assignees(auth.getName());
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request, Authentication auth) {
        return service.update(id, request, auth.getName());
    }

    @PatchMapping("/{id}/status")
    public TaskResponse changeStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest request,
            Authentication auth) {
        return service.changeStatus(id, request.status(), auth.getName());
    }
}
