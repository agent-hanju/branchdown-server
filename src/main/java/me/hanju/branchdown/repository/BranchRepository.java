package me.hanju.branchdown.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import me.hanju.branchdown.entity.BranchEntity;
import me.hanju.branchdown.entity.StreamEntity;
import me.hanju.branchdown.entity.id.BranchId;

public interface BranchRepository extends JpaRepository<BranchEntity, BranchId> {

  @Query("""
      SELECT b
      FROM BranchEntity b
      JOIN b.points p
      WHERE b.stream = :stream
      ORDER BY p.createdAt DESC
      LIMIT 1
      """)
  Optional<BranchEntity> findLatestBranchInChat(StreamEntity stream);
}
