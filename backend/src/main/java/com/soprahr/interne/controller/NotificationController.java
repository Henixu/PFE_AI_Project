package com.soprahr.interne.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soprahr.interne.dto.NotificationResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.dto.UnreadCountResponse;
import com.soprahr.interne.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return service.list(unreadOnly, page, size, auth.getName());
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication auth) {
        return new UnreadCountResponse(service.unreadCount(auth.getName()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, Authentication auth) {
        return service.markRead(id, auth.getName());
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication auth) {
        return Map.of("updated", service.markAllRead(auth.getName()));
    }
}
