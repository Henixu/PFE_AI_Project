package com.soprahr.interne.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.soprahr.interne.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Nombre d'employés (actifs ou non) par département. */
    interface DepartmentCount {
        Long getDepartmentId();

        long getTotal();
    }

    Optional<Employee> findByEmployeeNumberIgnoreCase(String employeeNumber);

    @Override
    @EntityGraph(attributePaths = {"department", "manager"})
    Page<Employee> findAll(Specification<Employee> specification, Pageable pageable);

    /** Charge département et manager en une requête (évite le N+1 des listes). */
    @EntityGraph(attributePaths = {"department", "manager"})
    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    long countByDepartmentId(Long departmentId);

    List<Employee> findByDepartmentIdOrderByLastNameAscFirstNameAsc(Long departmentId);

    @Query("select e.department.id as departmentId, count(e) as total from Employee e "
            + "where e.department.id in :ids group by e.department.id")
    List<DepartmentCount> countByDepartmentIds(@Param("ids") Collection<Long> ids);

    /** Collaborateurs directs d'un manager (actifs ou non). */
    List<Employee> findByManagerId(Long managerId);

    /** Employés actifs, triés par nom (liste de choix pour l'affectation). */
    List<Employee> findByActiveTrueOrderByLastNameAscFirstNameAsc();

    @EntityGraph(attributePaths = {"department"})
    Optional<Employee> findByUserId(Long userId);

    /** Employés rattachés à un lot de comptes (une seule requête pour une page d'utilisateurs). */
    List<Employee> findByUserIdIn(Collection<Long> userIds);

    /** Employés sans compte de connexion. */
    List<Employee> findByUserIsNullOrderByLastNameAscFirstNameAsc();

    @EntityGraph(attributePaths = {"department", "manager", "user"})
    Optional<Employee> findWithDetailsById(Long id);
}
