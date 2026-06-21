package me.hanju.branchdown.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import me.hanju.branchdown.entity.PointEntity;

public interface PointRepository extends JpaRepository<PointEntity, Long> {

  @Query("""
      SELECT p
      FROM PointEntity p
      WHERE p.stream.id = :streamId
      ORDER BY p.depth ASC, p.branchNum ASC, p.id ASC
      """)
  List<PointEntity> findAllByStreamId(Long streamId);

  /**
   * 특정 브랜치 경로에서 depth를 초과하는 Point들을 조회합니다.
   *
   * @param streamId   스트림 ID
   * @param branchNums 브랜치 경로 (path를 파싱한 결과 + 자기 자신의 branchNum)
   * @param depth      지정 depth (이 depth 이후의 Point들을 조회)
   * @return 지정한 경로의 depth 초과 Point 목록 (depth 오름차순)
   */
  @Query(value = """
      SELECT * FROM points AS p
      WHERE p.stream_id = :streamId
        AND (p.depth, p.branch_num) IN (
          SELECT p2.depth, MAX(p2.branch_num)
          FROM points AS p2
          WHERE p2.stream_id = :streamId
            AND p2.branch_num IN :branchNums
          GROUP BY p2.depth
        )
        AND p.depth > :depth
      ORDER BY p.depth
      """, nativeQuery = true)
  List<PointEntity> findAllUsingPath(
      Long streamId,
      List<Integer> branchNums,
      int depth);

  /**
   * 특정 브랜치 경로 내에서 루트(depth=0) 초과, depth 이하의 Point들을 반환합니다.
   *
   * @param streamId   스트림 ID
   * @param branchNums 브랜치 경로 (path를 파싱한 결과 + 자기 자신의 branchNum)
   * @param depth      최대 depth (이 depth 이하의 Point들을 조회, 자기 자신 포함, 루트 제외)
   * @return 자신 포함 조상 Point 목록 (depth 오름차순, 루트 제외)
   */
  @Query(value = """
      SELECT * FROM points AS p
      WHERE p.stream_id = :streamId
        AND (p.depth, p.branch_num) IN (
          SELECT p2.depth, MAX(p2.branch_num)
          FROM points AS p2
          WHERE p2.stream_id = :streamId
            AND p2.branch_num IN :branchNums
          GROUP BY p2.depth
        )
        AND p.depth > 0
        AND p.depth <= :depth
      ORDER BY p.depth
      """, nativeQuery = true)
  List<PointEntity> findAncestorsUsingPath(
      Long streamId,
      List<Integer> branchNums,
      int depth);
}
