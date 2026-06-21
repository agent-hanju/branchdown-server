package me.hanju.branchdown.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import me.hanju.branchdown.entity.StreamEntity;

public interface StreamRepository extends JpaRepository<StreamEntity, Long> {
}
