package com.soprahr.interne.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soprahr.interne.entity.LeaveRequest;
import com.soprahr.interne.entity.enums.LeaveStatus;

import jakarta.persistence.LockModeType;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    @EntityGraph(attributePaths = {"employee"})
    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByEmployeeId(Long employeeId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.manager", "employee.department", "decidedBy"})
    Page<LeaveRequest> findAll(Specification<LeaveRequest> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "employee.manager", "employee.department", "decidedBy"})
    Optional<LeaveRequest> findWithDetailsById(Long id);

    /** Verrouille la demande le temps de la décision : deux décisions simultanées ne peuvent pas se doubler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LeaveRequest l where l.id = :id")
    Optional<LeaveRequest> findForUpdateById(@Param("id") Long id);

    /** Une demande de l'employé, dans l'un des statuts donnés, recoupe-t-elle la période [start, end] ? */
    @Query("select count(l) > 0 from LeaveRequest l where l.employee.id = :employeeId and l.status in :statuses "
            + "and l.startDate <= :end and l.endDate >= :start")
    boolean existsOverlap(@Param("employeeId") Long employeeId, @Param("statuses") Collection<LeaveStatus> statuses,
            @Param("start") LocalDate start, @Param("end") LocalDate end);
}
