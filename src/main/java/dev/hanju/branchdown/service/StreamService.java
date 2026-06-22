package dev.hanju.branchdown.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dev.hanju.branchdown.constant.StreamConstants;
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.dto.StreamDto;
import dev.hanju.branchdown.entity.BranchEntity;
import dev.hanju.branchdown.entity.PointEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.BranchId;
import dev.hanju.branchdown.repository.BranchRepository;
import dev.hanju.branchdown.repository.PointRepository;
import dev.hanju.branchdown.repository.StreamRepository;
import dev.hanju.branchdown.util.PathUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

  private static final String STREAM_NOT_FOUND = "stream not found";

  private final StreamRepository streamRepository;
  private final BranchRepository branchRepository;
  private final PointRepository pointRepository;

  /**
   * 스트림 생성.
   * 스트림 + 초기 브랜치(path="") + depth=0 synthetic root 포인트(itemId=null)를 생성합니다.
   * root는 데이터를 담지 않는 구조적 앵커이므로 itemId를 받지 않습니다.
   *
   * @return 생성된 Stream과 root Point 응답
   */
  @Transactional
  public StreamDto.WithRootResponse createStream() {
    // 1. 스트림 생성
    final StreamEntity newStream = streamRepository.save(
        StreamEntity.builder().build());

    // 2. 스트림의 기본 브랜치 생성
    final BranchEntity initialBranch = branchRepository.save(
        BranchEntity.builder()
            .id(new BranchId(newStream.getId(), newStream.getNextBranchNum()))
            .stream(newStream)
            .path("")
            .build());
    newStream.addBranch(initialBranch);

    // 3. 스트림의 synthetic root 포인트 생성 (itemId 미지정 = null)
    final PointEntity rootPoint = PointEntity.builder()
        .branch(initialBranch)
        .depth(StreamConstants.ROOT_POINT_DEPTH)
        .build();
    pointRepository.save(rootPoint);

    initialBranch.addPoint(rootPoint);

    return new StreamDto.WithRootResponse(
        newStream.getId(),
        rootPoint.toResponse(),
        newStream.getCreatedAt());
  }

  /**
   * 스트림 조회
   *
   * @param id 스트림 ID
   * @return StreamDto.Response
   */
  @Transactional(readOnly = true)
  public StreamDto.Response getStream(final Long id) {
    final StreamEntity stream = streamRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));
    return stream.toResponse();
  }

  /**
   * 해당 스트림에서 다음에 발급할 branchNum을 반환합니다.
   * Tree 부분 로드 후 appendChild할 때도 전체 스트림 기준 branchNum을 이어가기 위해 사용합니다.
   *
   * @param id 스트림 ID
   * @return 다음 branchNum
   */
  @Transactional(readOnly = true)
  public int getNextBranchNum(final Long id) {
    return streamRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND))
        .getNextBranchNum();
  }

  /**
   * 스트림 삭제
   *
   * @param id 삭제할 스트림 ID
   */
  public void deleteStream(final Long id) {
    streamRepository.delete(
        streamRepository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND)));
  }

  /**
   * 해당 스트림의 처음부터(root 포함) 가장 최근에 포인트를 추가한 브랜치까지의 포인트 목록을 반환
   *
   * @param id 스트림 ID
   * @return PointDto.Response 목록
   */
  @Transactional(readOnly = true)
  public List<PointDto.Response> getStreamPoints(final Long id) {
    final StreamEntity stream = streamRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));
    final BranchEntity latestBranch = branchRepository
        .findLatestBranchInChat(stream)
        .orElseThrow(() -> new IllegalStateException("Latest Branch not found"));

    // latestBranch를 포함한 IntArray path 구하기
    final int[] branchNums = PathUtils.parse(
        PathUtils.append(latestBranch.getPath(), latestBranch.getBranchNum()));
    // 해당 path의 root부터 끝까지의 point 반환
    return this.getPointsByPath(stream.getId(), branchNums, -1)
        .stream()
        .map(PointEntity::toResponse)
        .toList();
  }

  /**
   * 해당 스트림에 속한 모든 포인트를 반환합니다.
   * 전체 Tree를 복원할 때처럼 브랜치 경로 하나가 아니라 스트림 전체 구조가 필요할 때 사용합니다.
   *
   * @param id 스트림 ID
   * @return 해당 스트림의 모든 Point 목록
   */
  @Transactional(readOnly = true)
  public List<PointDto.Response> getAllStreamPoints(final Long id) {
    final List<PointEntity> points = pointRepository.findAllByStreamId(id);
    if (points.isEmpty()) {
      throw new NoSuchElementException(STREAM_NOT_FOUND);
    }
    return points.stream()
        .map(PointEntity::toResponse)
        .toList();
  }

  /**
   * 해당 스트림의 depth 이후부터(depth 초과) branchNum에 해당하는 포인트 목록을 반환
   *
   * @param id        스트림 ID
   * @param branchNum 브랜치 번호
   * @param depth     시작 depth (이 depth 초과의 포인트들을 반환)
   * @return PointDto.Response 목록
   */
  @Transactional(readOnly = true)
  public List<PointDto.Response> getBranchMessages(
      final Long id,
      final int branchNum,
      final int depth) {
    final StreamEntity stream = streamRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));

    final BranchEntity branch = branchRepository
        .findById(new BranchId(stream.getId(), branchNum))
        .orElseThrow(() -> new IllegalArgumentException("Branch not found"));

    final int[] branchNums = PathUtils.parse(
        PathUtils.append(branch.getPath(), branchNum));

    return this.getPointsByPath(stream.getId(), branchNums, depth)
        .stream()
        .map(PointEntity::toResponse)
        .toList();
  }

  /**
   * 브랜치 경로를 따라 포인트 목록을 조회합니다.
   *
   * @param streamId   스트림 ID
   * @param branchNums 브랜치 경로 (path를 파싱한 결과 + 자기 자신의 branchNum)
   * @param depth      시작 depth (이 depth 초과의 포인트들을 반환, -1이면 root부터)
   * @return 포인트 목록 (depth 오름차순)
   */
  public List<PointEntity> getPointsByPath(
      final Long streamId,
      final int[] branchNums,
      final int depth) {
    final List<PointEntity> points = pointRepository.findAllUsingPath(
        streamId,
        Arrays.stream(branchNums).boxed().toList(),
        depth);

    // 위 쿼리는 각 depth 별 최대 branchNum인 point들을 가져오므로 branchNum 변곡점에서 절삭
    int i = 0;
    int maxBranchNum = 0;
    final List<PointEntity> clippedPoints = new ArrayList<>(points.size());
    while (i < points.size() && maxBranchNum <= points.get(i).getBranchNum()) {
      clippedPoints.add(points.get(i));
      maxBranchNum = points.get(i).getBranchNum();
      i += 1;
    }
    return clippedPoints;
  }

  /**
   * 새로 appendChild된 노드들을 Stream에 일괄 저장합니다.
   * <p>
   * branch → point 순서로 insert하여 FK 제약을 만족시키고,
   * 이미 영속된 부모 point의 childBranchNums도 함께 갱신합니다.
   * </p>
   *
   * @param streamId        대상 스트림 ID
   * @param newBranches     새로 생성할 branch 명세 목록
   * @param newPoints       새로 생성할 point 명세 목록
   * @param parentUpdates   이미 영속된 부모 point의 childBranchNums 갱신 목록
   * @param nextBranchNum   저장 후 스트림에 반영할 nextBranchNum
   * @return 각 point 명세의 nodeKey → 저장된 Point ID 매핑
   */
  @Transactional
  public Map<Long, Long> persistAppendedNodes(
      final Long streamId,
      final List<NewBranchSpec> newBranches,
      final List<NewPointSpec> newPoints,
      final List<ParentPointUpdate> parentUpdates,
      final int nextBranchNum) {
    final StreamEntity stream = streamRepository
        .findById(streamId)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));

    // 새 branchNum이 현재 DB 카운터와 충돌하지 않는지 검증
    final int currentNext = stream.getNextBranchNum();
    for (final NewBranchSpec spec : newBranches) {
      if (spec.branchNum() < currentNext) {
        throw new IllegalStateException(
            "Branch number conflict: " + spec.branchNum() + " is already used (nextBranchNum=" + currentNext + ")");
      }
    }

    // 1. 새 branch 일괄 저장
    final Map<Integer, BranchEntity> branchByNum = new HashMap<>();
    for (final NewBranchSpec spec : newBranches) {
      final BranchEntity branch = branchRepository.save(
          BranchEntity.builder()
              .id(new BranchId(streamId, spec.branchNum()))
              .stream(stream)
              .path(spec.path())
              .build());
      stream.addBranch(branch);
      branchByNum.put(spec.branchNum(), branch);
    }

    // 기존에 저장된 branch도 조회 대상에 포함 (branchNum이 부모와 같은 노드는 새 branch 불필요)
    for (final NewPointSpec spec : newPoints) {
      if (!branchByNum.containsKey(spec.branchNum())) {
        branchByNum.put(spec.branchNum(),
            branchRepository.findById(new BranchId(streamId, spec.branchNum()))
                .orElseThrow(() -> new IllegalStateException("Branch not found: " + spec.branchNum())));
      }
    }

    // 2. 새 point 일괄 저장
    final Map<Long, Long> nodeKeyToPointId = new HashMap<>();
    for (final NewPointSpec spec : newPoints) {
      final PointEntity point = pointRepository.save(
          PointEntity.builder()
              .branch(branchByNum.get(spec.branchNum()))
              .depth(spec.depth())
              .itemId(spec.itemId())
              .childBranchNums(spec.childBranchNums())
              .build());
      branchByNum.get(spec.branchNum()).addPoint(point);
      nodeKeyToPointId.put(spec.nodeKey(), point.getId());
    }

    // 3. 영속 부모 point의 childBranchNums 갱신
    for (final ParentPointUpdate update : parentUpdates) {
      final PointEntity parentPoint = pointRepository
          .findById(update.pointId())
          .orElseThrow(() -> new IllegalStateException("Parent point not found: " + update.pointId()));
      parentPoint.setChildBranchNums(update.childBranchNums());
    }

    // 4. stream.nextBranchNum 동기화
    stream.syncNextBranchNum(nextBranchNum);

    return nodeKeyToPointId;
  }

  /** 새 branch 생성 명세 */
  public record NewBranchSpec(int branchNum, String path) {
  }

  /** 새 point 생성 명세. nodeKey는 TreeNode를 식별하기 위한 임시 키. */
  public record NewPointSpec(long nodeKey, int branchNum, int depth, String itemId, int[] childBranchNums) {
  }

  /** 영속된 부모 point의 childBranchNums 갱신 명세 */
  public record ParentPointUpdate(long pointId, int[] childBranchNums) {
  }
}
