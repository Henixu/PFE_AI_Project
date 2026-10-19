package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.dto.DepartmentRequest;
import com.soprahr.interne.dto.DepartmentResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Department;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.DepartmentRepository;
import com.soprahr.interne.repository.EmployeeRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentServiceTest {

    private static final String ACTOR = "hr@test.local";

    @Mock DepartmentRepository departments;
    @Mock EmployeeRepository employees;
    @Mock AuditService audit;

    private DepartmentService service;
    private Department dev;
    private Department rh;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(departments, employees, audit);
        dev = TestData.department(1L, "Développement");
        rh = TestData.department(2L, "Ressources humaines");
        when(departments.findById(1L)).thenReturn(Optional.of(dev));
        when(departments.findById(2L)).thenReturn(Optional.of(rh));
        when(departments.findByNameIgnoreCase("développement")).thenReturn(Optional.of(dev));
        when(departments.findByNameIgnoreCase("Développement")).thenReturn(Optional.of(dev));
        when(departments.findByNameIgnoreCase("Ressources humaines")).thenReturn(Optional.of(rh));
        when(departments.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 99L);
            return d;
        });
    }

    @Test
    void createTrimsNameStoresBlankDescriptionAsNullAndAudits() {
        DepartmentResponse r = service.create(new DepartmentRequest("  Finance  ", "   "), ACTOR);

        assertEquals("Finance", r.name());
        assertNull(r.description());
        assertEquals(0, r.employeeCount());
        verify(audit).record(eq(ACTOR), eq("DEPARTMENT_CREATE"), eq("Department"), eq(99L), any());
    }

    @Test
    void createRejectsExistingNameIgnoringCase() {
        assertThrows(ConflictException.class,
                () -> service.create(new DepartmentRequest("développement", null), ACTOR));
        verify(departments, never()).save(any());
    }

    @Test
    void updateKeepingOwnNameIsAllowed() {
        DepartmentResponse r = service.update(1L, new DepartmentRequest("Développement", "Équipe logicielle"), ACTOR);

        assertEquals("Équipe logicielle", r.description());
        verify(audit).record(eq(ACTOR), eq("DEPARTMENT_UPDATE"), eq("Department"), eq(1L), any());
    }

    @Test
    void updateToAnotherDepartmentNameConflicts() {
        assertThrows(ConflictException.class,
                () -> service.update(1L, new DepartmentRequest("Ressources humaines", null), ACTOR));
    }

    @Test
    void updateUnknownDepartmentIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(404L, new DepartmentRequest("X", null), ACTOR));
    }

    @Test
    void deleteIsRefusedWhileEmployeesAreAttached() {
        when(employees.countByDepartmentId(1L)).thenReturn(3L);

        ConflictException e = assertThrows(ConflictException.class, () -> service.delete(1L, ACTOR));

        assertEquals("Ce département compte 3 employé(s) : réaffectez-les avant de le supprimer", e.getMessage());
        verify(departments, never()).delete(any(Department.class));
    }

    @Test
    void deleteEmptyDepartmentSucceedsAndAudits() {
        when(employees.countByDepartmentId(2L)).thenReturn(0L);

        service.delete(2L, ACTOR);

        verify(departments).delete(rh);
        verify(audit).record(eq(ACTOR), eq("DEPARTMENT_DELETE"), eq("Department"), eq(2L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReportsEmployeeCountPerDepartment() {
        when(departments.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dev, rh)));
        when(employees.countByDepartmentIds(any())).thenReturn(List.of(count(1L, 4)));

        PageResponse<DepartmentResponse> page = service.list("dév", 0, 10, "name", "asc");

        assertEquals(4, page.content().get(0).employeeCount());
        assertEquals(0, page.content().get(1).employeeCount());
        assertNull(page.content().get(0).employees());
    }

    @Test
    void getReturnsAssociatedEmployees() {
        Employee alice = TestData.employee(10L, "Alice", "Dupont", null, null);
        alice.setJobTitle("Développeuse");
        when(employees.findByDepartmentIdOrderByLastNameAscFirstNameAsc(1L)).thenReturn(List.of(alice));

        DepartmentResponse r = service.get(1L);

        assertEquals(1, r.employeeCount());
        assertEquals("Alice Dupont", r.employees().get(0).fullName());
    }

    private static EmployeeRepository.DepartmentCount count(Long departmentId, long total) {
        return new EmployeeRepository.DepartmentCount() {
            @Override
            public Long getDepartmentId() {
                return departmentId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
