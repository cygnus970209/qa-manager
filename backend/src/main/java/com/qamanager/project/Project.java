package com.qamanager.project;

import com.qamanager.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected Project() {}

    public Project(String name, String description, ProjectStatus status) {
        this.name = name;
        this.description = description;
        this.status = status.getCode();
    }

    public void update(String name, String description, ProjectStatus status) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (status != null) this.status = status.getCode();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return ProjectStatus.from(status); }
}
