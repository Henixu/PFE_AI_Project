package com.soprahr.interne.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "departments")
public class Department extends AuditableEntity {

    /** Unique sans tenir compte de la casse (index sur lower(name)). */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;
}
