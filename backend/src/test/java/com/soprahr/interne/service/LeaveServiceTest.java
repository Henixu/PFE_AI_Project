package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.dto.LeaveResponse;
import com.soprahr.interne.dto.LeaveSubmitRequest;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.LeaveRequest;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.LeaveStatus;
import com.soprahr.interne.entity.enums.LeaveType;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.LeaveRequestRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveServiceTest {

    @Mock LeaveRequestRepository leaves;
    @Mock EmployeeRepository employees;
    @Mock ActorResolver actors;
    @Mock AuditService audit;
    @Mock NotificationPublisher notifier;

    private LeaveService service;

    private User hrUser;
    private User managerUser;
    private User otherManagerUser;
    private User aliceUser;
    private User bobUser;
    private Employee alice;   // collaboratrice de Karim
    private Employee bob;     // collaborateur d'un autre manager

    @BeforeEach
    void setUp() {
        service = new LeaveService(leaves, employees, actors, audit, notifier);
        hrUser = TestData.user(1L, "hr@test.local", Role.HR);
        managerUser = TestData.user(2L, "manager@test.local", Role.MANAGER);
        otherManagerUser = TestData.user(5L, "other@test.local", Role.MANAGER);
        aliceUser = TestData.user(3L, "alice@test.local", Role.EMPLOYEE);
        bobUser = TestData.user(4L, "bob@test.local", Role.EMPLOYEE);
        Employee hr = TestData.employee(11L, "Rania", "Rh", hrUser, null);
        Employee manager = TestData.employee(12L, "Karim", "Manager", managerUser, null);
        Employee otherManager = TestData.employee(15L, "Omar", "Autre", otherManagerUser, null);
        alice = TestData.employee(13L, "Alice", "Dupont", aliceUser, manager);
        bob = TestData.employee(14L, "Bob", "Martin", bobUser, otherManager);

        when(actors.resolve("hr@test.local")).thenReturn(new CurrentActor(hrUser, hr));
        when(actors.resolve("manager@test.local")).thenReturn(new CurrentActor(managerUser, manager));
        when(actors.resolve("alice@test.local")).thenReturn(new CurrentActor(aliceUser, alice));
        when(actors.resolve("bob@test.local")).thenReturn(new CurrentActor(bobUser, bob));
        when(actors.resolve("other@test.local")).thenReturn(new CurrentActor(otherManagerUser, otherManager));
        when(actors.resolve("noemployee@test.local"))
                .thenReturn(new CurrentActor(TestData.user(9L, "noemployee@test.local", Role.EMPLOYEE), null));
        when(leaves.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest l = inv.getArgument(0);
            if (l.getId() == null) {
                ReflectionTestUtils.setField(l, "id", 50L);
            }
            return l;
        });
        when(employees.findByUserIdIn(anyCollection())).thenReturn(List.of(hr_()));
    }

    // ---- dépôt ----

    @Test
    void submitCreatesPendingRequestAndComputesDuration() {
        LeaveResponse r = service.submit(new LeaveSubmitRequest(null, date(5), date(7), "  Vacances "),
                "alice@test.local");

        assertEquals(LeaveStatus.PENDING, r.status());
        assertEquals(LeaveType.ANNUAL, r.type());
        assertEquals("Vacances", r.reason());
        assertEquals(3, r.durationDays());
        assertTrue(r.canCancel());
        assertFalse(r.canDecide());
        verify(audit).record(eq("alice@test.local"), eq("LEAVE_SUBMIT"), eq("LeaveRequest"), eq(50L), any());
    }

    @Test
    void submitAcceptsASingleDay() {
        assertEquals(1, service.submit(new LeaveSubmitRequest(LeaveType.SICK, date(5), date(5), "Grippe"),
                "alice@test.local").durationDays());
    }

    @Test
    void submitRejectsEndBeforeStart() {
        assertThrows(BusinessRuleException.class,
                () -> service.submit(new LeaveSubmitRequest(null, date(7), date(5), "x"), "alice@test.local"));
        verify(leaves, never()).save(any());
    }

    @Test
    void submitRejectsOverlapWithPendingOrApprovedRequest() {
        when(leaves.existsOverlap(eq(13L), anyCollection(), eq(date(5)), eq(date(7)))).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.submit(new LeaveSubmitRequest(null, date(5), date(7), "x"), "alice@test.local"));
        verify(leaves, never()).save(any());
    }

    @Test
    void submitRequiresALinkedEmployee() {
        assertThrows(BusinessRuleException.class,
                () -> service.submit(new LeaveSubmitRequest(null, date(5), date(7), "x"), "noemployee@test.local"));
    }

    // ---- décision ----

    @Test
    void hrApprovalRecordsAuthorDateAndComment() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));

        LeaveResponse r = service.approve(1L, " Bon repos ", "hr@test.local");

        assertEquals(LeaveStatus.APPROVED, r.status());
        assertEquals("Bon repos", r.decisionComment());
        assertEquals("Rania Rh", r.decidedBy());
        assertNotNull(r.decidedAt());
        assertEquals(hrUser, leave.getDecidedBy());
        assertFalse(r.canDecide());
        verify(audit).record(eq("hr@test.local"), eq("LEAVE_APPROVE"), eq("LeaveRequest"), eq(1L), any());
    }

    @Test
    void approvalNotifiesTheEmployeeWithTheComment() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));

        service.approve(1L, "Bon repos", "hr@test.local");

        verify(notifier).notifyEmployee(eq(alice), eq(NotificationType.LEAVE_DECISION), eq("Congé approuvé"),
                contains("approuvée. Commentaire : Bon repos"), eq("/leaves"));
    }

    @Test
    void rejectionRequiresAReason() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));

        assertThrows(BusinessRuleException.class, () -> service.reject(1L, "  ", "hr@test.local"));
        assertEquals(LeaveStatus.PENDING, leave.getStatus());

        LeaveResponse r = service.reject(1L, "Période de livraison", "hr@test.local");
        assertEquals(LeaveStatus.REJECTED, r.status());
        verify(audit).record(eq("hr@test.local"), eq("LEAVE_REJECT"), eq("LeaveRequest"), eq(1L), any());
        verify(notifier).notifyEmployee(eq(alice), eq(NotificationType.LEAVE_DECISION), eq("Congé refusé"),
                contains("refusée. Commentaire : Période de livraison"), eq("/leaves"));
    }

    @Test
    void noNotificationWhenTheDecisionIsRefused() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));

        assertThrows(BusinessRuleException.class, () -> service.reject(1L, " ", "hr@test.local"));
        assertThrows(AccessDeniedException.class, () -> service.approve(1L, null, "other@test.local"));

        verify(notifier, never()).notifyEmployee(any(), any(), any(), any(), any());
    }

    @Test
    void approvalWithoutCommentIsAllowed() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));

        assertNull(service.approve(1L, null, "hr@test.local").decisionComment());
    }

    @Test
    void managerDecidesOnlyForDirectReports() {
        LeaveRequest aliceLeave = pending(1L, alice);
        LeaveRequest bobLeave = pending(2L, bob);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(aliceLeave));
        when(leaves.findForUpdateById(2L)).thenReturn(Optional.of(bobLeave));

        assertThrows(AccessDeniedException.class, () -> service.approve(2L, null, "manager@test.local"));
        assertEquals(LeaveStatus.PENDING, bobLeave.getStatus());
        assertEquals(LeaveStatus.APPROVED, service.approve(1L, null, "manager@test.local").status());
    }

    @Test
    void nobodyDecidesTheirOwnRequest() {
        LeaveRequest hrLeave = pending(3L, hr_());
        when(leaves.findForUpdateById(3L)).thenReturn(Optional.of(hrLeave));

        assertThrows(BusinessRuleException.class, () -> service.approve(3L, null, "hr@test.local"));
        assertEquals(LeaveStatus.PENDING, hrLeave.getStatus());
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));
        service.approve(1L, "ok", "hr@test.local");

        assertThrows(ConflictException.class, () -> service.reject(1L, "finalement non", "hr@test.local"));
        assertEquals(LeaveStatus.APPROVED, leave.getStatus());
        assertEquals("ok", leave.getDecisionComment());
    }

    // ---- annulation et consultation ----

    @Test
    void ownerCancelsPendingRequestOnly() {
        LeaveRequest leave = pending(1L, alice);
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(leave));

        assertEquals(LeaveStatus.CANCELLED, service.cancel(1L, "alice@test.local").status());
        assertThrows(ConflictException.class, () -> service.cancel(1L, "alice@test.local"));
    }

    @Test
    void cannotCancelSomeoneElsesRequest() {
        when(leaves.findForUpdateById(1L)).thenReturn(Optional.of(pending(1L, alice)));

        assertThrows(ResourceNotFoundException.class, () -> service.cancel(1L, "bob@test.local"));
    }

    @Test
    void requestIsVisibleToOwnerHrAndManagerOfTheEmployeeOnly() {
        when(leaves.findWithDetailsById(1L)).thenReturn(Optional.of(pending(1L, alice)));

        assertEquals(1L, service.get(1L, "alice@test.local").id());
        assertEquals(1L, service.get(1L, "hr@test.local").id());
        assertTrue(service.get(1L, "manager@test.local").canDecide());
        assertThrows(ResourceNotFoundException.class, () -> service.get(1L, "bob@test.local"));
        assertThrows(ResourceNotFoundException.class, () -> service.get(1L, "other@test.local"));
    }

    // ---- utilitaires ----

    private Employee hr_() {
        return TestData.employee(11L, "Rania", "Rh", hrUser, null);
    }

    private static LocalDate date(int offsetDays) {
        return LocalDate.now().plusDays(offsetDays);
    }

    private static LeaveRequest pending(long id, Employee employee) {
        LeaveRequest leave = new LeaveRequest();
        ReflectionTestUtils.setField(leave, "id", id);
        leave.setEmployee(employee);
        leave.setType(LeaveType.ANNUAL);
        leave.setStartDate(date(10));
        leave.setEndDate(date(12));
        leave.setReason("Vacances");
        return leave;
    }
}
