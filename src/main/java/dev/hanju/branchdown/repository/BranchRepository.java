package dev.hanju.branchdown.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.hanju.branchdown.entity.BranchEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.BranchId;

public interface BranchRepository extends JpaRepository<BranchEntity, BranchId> {

  @Query("""
      SELECT b
      FROM BranchEntity b
      JOIN b.points p
      WHERE b.stream = :stream
      ORDER BY p.id.seq DESC
      LIMIT 1
      """)
  Optional<BranchEntity> findLatestBranchInStream(StreamEntity stream);
}
