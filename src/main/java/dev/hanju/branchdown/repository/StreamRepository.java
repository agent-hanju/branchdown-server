package dev.hanju.branchdown.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import dev.hanju.branchdown.entity.StreamEntity;

public interface StreamRepository extends JpaRepository<StreamEntity, Long> {

  /**
   * point 추가 시 nextBranchNum과 nextSeq의 발급 유일성을 위해 비관적 락을 걸고 조회
   * @param id 조회할 Stream ID 
   * @return 해당 트랜잭션 내에서 락이 걸린 채로 StreamEntity 반환
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM StreamEntity s WHERE s.id = :id")
  Optional<StreamEntity> findByIdForUpdate(Long id);
}
