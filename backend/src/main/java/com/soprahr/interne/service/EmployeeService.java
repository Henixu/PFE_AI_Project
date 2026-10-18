package com.soprahr.interne.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.DepartmentOptionResponse;
import com.soprahr.interne.dto.EmployeeRequest;
import com.soprahr.interne.dto.EmployeeResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Department;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.DepartmentRepository;
import com.soprahr.interne.repository.EmployeeRepository;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
@Transactional
public class EmployeeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("employeeNumber", "lastName", "firstName", "hireDate", "active");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public EmployeeService(EmployeeRepository employees, DepartmentRepository departments, AuditService audit) {
        this.employees = employees;
        this.departments = departments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(String search, Long departmentId, Boolean active, int page, int size,
            String sortBy, String direction) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        SORTABLE.contains(sortBy) ? sortBy : "lastName"));
        Page<Employee> result = employees.findAll(filter(search, departmentId, active), pageable);
        return PageResponse.of(result, result.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return toResponse(employees.findWithDetailsById(id).orElseThrow(() -> notFound(id)));
    }

    public EmployeeResponse create(EmployeeRequest request, String actor) {
        validateNumber(request.employeeNumber(), null);
        Employee employee = new Employee();
        apply(employee, request);
        employee = employees.save(employee);
        audit.record(actor, "EMPLOYEE_CREATE", "Employee", employee.getId(), "employeeNumber=" + employee.getEmployeeNumber());
        return toResponse(employee);
    }

    public EmployeeResponse update(Long id, EmployeeRequest request, String actor) {
        Employee employee = find(id);
        validateNumber(request.employeeNumber(), id);
        apply(employee, request);
        audit.record(actor, "EMPLOYEE_UPDATE", "Employee", id, "employeeNumber=" + employee.getEmployeeNumber());
        return toResponse(employee);
    }

    public EmployeeResponse setActive(Long id, boolean active, String actor) {
        Employee employee = find(id);
        employee.setActive(active);
        audit.record(actor, active ? "EMPLOYEE_ENABLE" : "EMPLOYEE_DISABLE", "Employee", id, "active=" + active);
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public java.util.List<DepartmentOptionResponse> departments() {
        return departments.findAll(Sort.by("name")).stream()
                .map(d -> new DepartmentOptionResponse(d.getId(), d.getName())).toList();
    }

    private Employee find(Long id) {
        return employees.findWithDetailsById(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Employé introuvable: " + id);
    }

    private void validateNumber(String number, Long id) {
        employees.findByEmployeeNumberIgnoreCase(number.trim()).ifPresent(existing -> {
            if (id == null || !existing.getId().equals(id)) {
                throw new ConflictException("Ce matricule est déjà utilisé");
            }
        });
    }

    private void apply(Employee employee, EmployeeRequest request) {
        employee.setEmployeeNumber(request.employeeNumber().trim());
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setJobTitle(blankToNull(request.jobTitle()));
        employee.setPhone(blankToNull(request.phone()));
        employee.setHireDate(request.hireDate());
        employee.setDepartment(request.departmentId() == null ? null : departments.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Département introuvable")));
        if (request.managerId() != null && employee.getId() != null && request.managerId().equals(employee.getId())) {
            throw new ConflictException("Un employé ne peut pas être son propre manager");
        }
        employee.setManager(request.managerId() == null ? null : employees.findById(request.managerId())
                .orElseThrow(() -> new ResourceNotFoundException("Manager introuvable")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Specification<Employee> filter(String search, Long departmentId, Boolean active) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                        .replace("%", "\\%").replace("_", "\\_") + "%";
                var text = cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName")));
                predicates.add(cb.or(cb.like(text, pattern, '\\'),
                    cb.like(cb.lower(root.get("employeeNumber")), pattern, '\\'),
                    cb.like(cb.lower(root.get("jobTitle")), pattern, '\\')));
            }
            if (departmentId != null) predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFirstName(), e.getLastName(), e.getJobTitle(),
                e.getPhone(), e.getHireDate(), e.isActive(), e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(), e.getManager() == null ? null : e.getManager().getId(),
                e.getManager() == null ? null : e.getManager().getFirstName() + " " + e.getManager().getLastName());
    }
}