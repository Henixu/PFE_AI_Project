package com.soprahr.interne.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.DepartmentEmployeeResponse;
import com.soprahr.interne.dto.DepartmentRequest;
import com.soprahr.interne.dto.DepartmentResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Department;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.DepartmentRepository;
import com.soprahr.interne.repository.EmployeeRepository;

/** Gestion des départements, réservée aux rôles ADMIN et HR. Chaque écriture est auditée. */
@Service
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
@Transactional
public class DepartmentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("name", "createdAt");

    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final AuditService audit;

    public DepartmentService(DepartmentRepository departments, EmployeeRepository employees, AuditService audit) {
        this.departments = departments;
        this.employees = employees;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> list(String search, int page, int size, String sortBy, String direction) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        SORTABLE.contains(sortBy) ? sortBy : "name"));
        Page<Department> result = departments.findAll(matching(search), pageable);
        Map<Long, Long> counts = counts(result.getContent());
        return PageResponse.of(result, result.getContent().stream()
                .map(d -> toResponse(d, counts.getOrDefault(d.getId(), 0L), null)).toList());
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long id) {
        Department department = find(id);
        List<DepartmentEmployeeResponse> members = employees.findByDepartmentIdOrderByLastNameAscFirstNameAsc(id)
                .stream()
                .map(e -> new DepartmentEmployeeResponse(e.getId(), e.getEmployeeNumber(),
                        e.getFirstName() + " " + e.getLastName(), e.getJobTitle(), e.isActive()))
                .toList();
        return toResponse(department, members.size(), members);
    }

    public DepartmentResponse create(DepartmentRequest request, String actor) {
        String name = request.name().trim();
        assertNameAvailable(name, null);
        Department department = new Department();
        department.setName(name);
        department.setDescription(blankToNull(request.description()));
        department = departments.save(department);
        audit.record(actor, "DEPARTMENT_CREATE", "Department", department.getId(), "name=" + name);
        return toResponse(department, 0, null);
    }

    public DepartmentResponse update(Long id, DepartmentRequest request, String actor) {
        Department department = find(id);
        String name = request.name().trim();
        assertNameAvailable(name, id);
        String previous = department.getName();
        department.setName(name);
        department.setDescription(blankToNull(request.description()));
        audit.record(actor, "DEPARTMENT_UPDATE", "Department", id, "name: " + previous + " -> " + name);
        return toResponse(department, employees.countByDepartmentId(id), null);
    }

    /** Un département rattaché à des employés (actifs ou non) n'est jamais supprimé : il faut d'abord les réaffecter. */
    public void delete(Long id, String actor) {
        Department department = find(id);
        long count = employees.countByDepartmentId(id);
        if (count > 0) {
            throw new ConflictException("Ce département compte " + count
                    + " employé(s) : réaffectez-les avant de le supprimer");
        }
        departments.delete(department);
        audit.record(actor, "DEPARTMENT_DELETE", "Department", id, "name=" + department.getName());
    }

    private Department find(Long id) {
        return departments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Département introuvable: " + id));
    }

    private void assertNameAvailable(String name, Long selfId) {
        departments.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new ConflictException("Un département porte déjà ce nom");
            }
        });
    }

    private Map<Long, Long> counts(List<Department> content) {
        if (content.isEmpty()) {
            return Map.of();
        }
        return employees.countByDepartmentIds(content.stream().map(Department::getId).toList()).stream()
                .collect(Collectors.toMap(EmployeeRepository.DepartmentCount::getDepartmentId,
                        EmployeeRepository.DepartmentCount::getTotal));
    }

    private static Specification<Department> matching(String search) {
        String pattern = SqlLike.contains(search);
        return (root, query, cb) -> pattern == null
                ? cb.conjunction()
                : cb.or(cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, '\\'));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static DepartmentResponse toResponse(Department d, long count, List<DepartmentEmployeeResponse> members) {
        return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), count, d.getCreatedAt(),
                d.getUpdatedAt(), members);
    }
}
