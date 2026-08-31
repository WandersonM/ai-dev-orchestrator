package com.ordevia.aidev.project.infrastructure;

import com.ordevia.aidev.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {}
