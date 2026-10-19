package com.soprahr.interne.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.soprahr.interne.dto.LeaveDecisionRequest;
import com.soprahr.interne.dto.LeaveResponse;
import com.soprahr.interne.dto.LeaveSubmitRequest;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.enums.LeaveStatus;
import com.soprahr.interne.service.LeaveService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse submit(@Valid @RequestBody LeaveSubmitRequest request, Authentication auth) {
        return service.submit(request, auth.getName());
    }

    /** Demandes de l'utilisateur connecté. */
    @GetMapping("/mine")
    public PageResponse<LeaveResponse> mine(@RequestParam(required = false) LeaveStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "desc") String direction,
            Authentication auth) {
        return service.listMine(status, page, size, sortBy, direction, auth.getName());
    }

    /** Demandes à traiter, dans le périmètre de l'utilisateur (ADMIN, HR : tous ; MANAGER : son équipe). */
    @GetMapping
    public PageResponse<LeaveResponse> toProcess(@RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) Long employeeId, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "desc") String direction,
            Authentication auth) {
        return service.listToProcess(status, employeeId, search, page, size, sortBy, direction, auth.getName());
    }

    @GetMapping("/{id}")
    public LeaveResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName());
    }

    @PatchMapping("/{id}/approve")
    public LeaveResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDecisionRequest body,
            Authentication auth) {
        return service.approve(id, body == null ? null : body.comment(), auth.getName());
    }

    @PatchMapping("/{id}/reject")
    public LeaveResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDecisionRequest body,
            Authentication auth) {
        return service.reject(id, body == null ? null : body.comment(), auth.getName());
    }

    @PatchMapping("/{id}/cancel")
    public LeaveResponse cancel(@PathVariable Long id, Authentication auth) {
        return service.cancel(id, auth.getName());
    }
}
