package dev.hanju.branchdown.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import dev.hanju.branchdown.entity.StreamEntity;

public interface StreamRepository extends JpaRepository<StreamEntity, Long> {
}
