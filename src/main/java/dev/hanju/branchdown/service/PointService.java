package dev.hanju.branchdown.service;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.entity.BranchEntity;
import dev.hanju.branchdown.entity.PointEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.BranchId;
import dev.hanju.branchdown.repository.BranchRepository;
import dev.hanju.branchdown.repository.PointRepository;
import dev.hanju.branchdown.util.PathUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

  private final PointRepository pointRepository;
  private final BranchRepository branchRepository;

  /**
   * 지정한 PointEntity 아래에 적절한 브랜칭 후 PointEntity를 새로 추가한다.
   *
   * @param id     지정할 PointEntity의 id
   * @param itemId 새로 추가할 PointEntity에 들어갈 item의 ID
   * @return 생성된 포인트 응답
   */
  @Transactional
  public PointDto.Response pointDown(Long id, String itemId) {
    // 1. 기준 포인트 확인
    PointEntity point = pointRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Point not found"));

    // 2. 브랜치 결정
    BranchEntity branch = point.getBranch();
    if (point.getChildBranchNums().length > 0) {
      final BranchEntity parentBranch = point.getBranch();
      final StreamEntity stream = parentBranch.getStream();
      final String newPath = PathUtils.append(
          parentBranch.getPath(),
          parentBranch.getBranchNum());
      branch = branchRepository.save(
          BranchEntity.builder()
              .id(new BranchId(stream.getId(), stream.getNextBranchNum()))
              .stream(stream)
              .path(newPath)
              .build());
      stream.addBranch(branch);
    }
    point.addChildBranchNum(branch.getBranchNum());

    // 3. 포인트 추가
    PointEntity newPoint = pointRepository.save(
        PointEntity.builder()
            .branch(branch)
            .depth(point.getDepth() + 1)
            .itemId(itemId)
            .build());
    branch.addPoint(newPoint);

    return newPoint.toResponse();
  }

  /**
   * 특정 Point와 그 조상 Point들을 조회합니다.
   * 같은 branch 경로 내에서 자신을 포함한 상위 depth의 Point들을 반환합니다.
   * 루트 포인트는 제외됩니다.
   *
   * @param id 기준 Point의 ID
   * @return 자신 포함 조상 Point 목록 (depth 오름차순, 루트 제외)
   */
  public List<PointDto.Response> getAncestors(Long id) {
    PointEntity point = pointRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Point not found"));

    BranchEntity branch = point.getBranch();
    Long streamId = branch.getStream().getId();

    // branch의 path를 파싱하여 경로에 포함된 branchNum 목록 생성
    int[] branchNums = PathUtils.append(
        PathUtils.parse(branch.getPath()),
        branch.getBranchNum());

    List<PointEntity> ancestors = pointRepository.findAncestorsUsingPath(
        streamId,
        Arrays.stream(branchNums).boxed().toList(),
        point.getDepth());

    return ancestors.stream().map(PointEntity::toResponse).toList();
  }
}
