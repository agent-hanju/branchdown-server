package dev.hanju.branchdown.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dev.hanju.branchdown.BranchdownTree;
import dev.hanju.branchdown.BranchdownTreeNode;
import dev.hanju.branchdown.constant.BranchdownConstants;
import dev.hanju.branchdown.constant.TraversalType;
import dev.hanju.branchdown.dto.BranchDto;
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.dto.StreamDto;
import dev.hanju.branchdown.entity.BranchEntity;
import dev.hanju.branchdown.entity.PointEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.BranchId;
import dev.hanju.branchdown.entity.id.PointId;
import dev.hanju.branchdown.repository.BranchRepository;
import dev.hanju.branchdown.repository.PointRepository;
import dev.hanju.branchdown.repository.StreamRepository;
import dev.hanju.branchdown.util.PathUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

  private static final String STREAM_NOT_FOUND = "stream not found";

  @PersistenceContext
  private EntityManager entityManager;

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

    // 3. 스트림의 root 포인트 생성
    final PointEntity savedRootPoint = pointRepository.save(
        PointEntity.builder()
            .id(new PointId(newStream.getId(), newStream.getNextSeq()))
            .stream(newStream)
            .branch(initialBranch)
            .branchNum(initialBranch.getBranchNum())
            .depth(BranchdownConstants.ROOT_DEPTH)
            .build());
    initialBranch.addPoint(savedRootPoint);
    newStream.addPoint(savedRootPoint);

    return new StreamDto.WithRootResponse(
        newStream.getId(),
        savedRootPoint.toResponse(),
        newStream.getCreatedAt(),
        newStream.getNextSeq(),
        newStream.getNextBranchNum());
  }

  /**
   * 스트림 조회
   *
   * @param id 스트림 ID
   * @return StreamDto.Response
   */
  @Transactional(readOnly = true)
  public StreamDto.WithRootResponse getStream(final Long id) {
    final PointEntity root = pointRepository.findRootByStreamId(id)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));
    final StreamEntity stream = root.getStream();
    return new StreamDto.WithRootResponse(id, root.toResponse(), stream.getCreatedAt(), stream.getNextSeq(), stream.getNextBranchNum());
  }

  /**
   * in-memory tree의 미저장 노드를 1개 트랜잭션 내에서 배치로 DB에 저장합니다.
   *
   * @param streamId 저장 대상 스트림 ID
   * @param tree     동기화할 in-memory tree
   */
  @Transactional
  public void saveTree(@Nonnull final Long streamId, @Nonnull final BranchdownTree<String> tree) {
    final StreamEntity stream = streamRepository.findByIdForUpdate(streamId)
        .orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));
    final int dbNextSeq = stream.getNextSeq();
    final int dbNextBranchNum = stream.getNextBranchNum();

    // 1단계: BFS 순회 → 신규 BranchDto/PointDto 수집
    final List<BranchDto.Internal> newBranchDtos = new ArrayList<>();
    final List<PointDto.Internal> newPointDtos = new ArrayList<>();
    final Map<Integer, Integer> existingParentUpdates = new HashMap<>();

    final Iterator<BranchdownTreeNode<String>> it = tree.traversal(tree.getRoot(), TraversalType.BFS);
    while (it.hasNext()) {
      final BranchdownTreeNode<String> node = it.next();
      final BranchdownTreeNode<String> parent = tree.getParent(node);
      final int depth = tree.getDepth(node.getId());

      final int branchNum = node.getBranchNum();
      if (parent != null && branchNum != parent.getBranchNum() && branchNum >= dbNextBranchNum) {
        newBranchDtos.add(new BranchDto.Internal(branchNum,
            PathUtils.joinWithComma(tree.getBranchPath(node.getId()))));
        if (parent.getSeq() < dbNextSeq)
          existingParentUpdates.put(parent.getSeq(), branchNum);
      }
      if (node.getSeq() >= dbNextSeq)
        newPointDtos.add(new PointDto.Internal(node.getSeq(), branchNum, depth, node.getItem(), node.getChildBranchNums()));
    }

    if (newPointDtos.isEmpty()) return;

    // 2단계: DTO → Entity 변환 후 일괄 persist
    if (!existingParentUpdates.isEmpty())
      pointRepository.findAllById(
          existingParentUpdates.keySet().stream().map(seq -> new PointId(streamId, seq)).toList()
      ).forEach(p -> p.addChildBranchNum(existingParentUpdates.get(p.getSeq())));

    final List<BranchEntity> newBranchEntities = newBranchDtos.stream()
        .map(dto -> BranchEntity.builder()
            .id(new BranchId(streamId, dto.branchNum()))
            .stream(stream)
            .path(dto.path())
            .build())
        .toList();
    newBranchEntities.forEach(entityManager::persist);

    final List<PointEntity> newPointEntities = newPointDtos.stream()
        .map(dto -> PointEntity.builder()
            .id(new PointId(streamId, dto.seq()))
            .stream(stream)
            .branch(entityManager.getReference(BranchEntity.class, new BranchId(streamId, dto.branchNum())))
            .branchNum(dto.branchNum())
            .depth(dto.depth())
            .itemId(dto.itemId())
            .childBranchNums(dto.childBranchNums())
            .build())
        .toList();
    newPointEntities.forEach(entityManager::persist);
    stream.syncBranchesAndPoints(newBranchEntities, newPointEntities);
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
  public StreamDto.Internal getLatestBranchPoints(final Long id) {
    final StreamEntity stream = streamRepository.findById(id).orElseThrow(() -> new NoSuchElementException(STREAM_NOT_FOUND));
    final BranchEntity branch = branchRepository.findLatestBranchInStream(stream).orElseThrow(()->new NoSuchElementException("branch not found"));
    return new StreamDto.Internal(getPointsByPath(id, PathUtils.parse(PathUtils.append(branch.getPath(), branch.getBranchNum())), -1).stream().map(PointEntity::toResponse).toList(), stream.getNextSeq(), stream.getNextBranchNum());
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
  public StreamDto.Internal getBranchPoints(
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

    final List<PointDto.Response> points = this.getPointsByPath(stream.getId(), branchNums, depth)
        .stream()
        .map(PointEntity::toResponse)
        .toList();
    return new StreamDto.Internal(points, stream.getNextSeq(), stream.getNextBranchNum());
  }

    /**
   * 지정한 PointEntity 아래에 적절한 브랜칭 후 PointEntity를 새로 추가한다.
   *
   * @param streamId 기준 포인트가 속한 스트림 ID
   * @param seq      기준 포인트의 seq
   * @param itemId   새로 추가할 PointEntity에 들어갈 item의 ID
   * @return 생성된 포인트 응답
   */
  @Transactional
  public PointDto.Response pointDown(Long streamId, int seq, String itemId) {
    // 1.비관적 stream 락 획득
    final StreamEntity stream = streamRepository
        .findByIdForUpdate(streamId)
        .orElseThrow(() -> new NoSuchElementException("Stream not found"));
    
    // 2. 기준 포인트 확인
    final PointEntity point = pointRepository
        .findById(new PointId(streamId, seq))
        .orElseThrow(() -> new NoSuchElementException("Point not found"));

    // 3. 브랜치 결정
    BranchEntity branch = point.getBranch();
    if (point.getChildBranchNums().length > 0) {
      final BranchEntity parentBranch = point.getBranch();
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
            .id(new PointId(streamId, stream.getNextSeq()))
            .stream(stream)
            .branch(branch)
            .branchNum(branch.getBranchNum())
            .depth(point.getDepth() + 1)
            .itemId(itemId)
            .build());
    branch.addPoint(newPoint);
    stream.addPoint(newPoint);

    return newPoint.toResponse();
  }

  /**
   * 특정 Point와 그 조상 Point들을 조회합니다.
   * 같은 branch 경로 내에서 자신을 포함한 상위 depth의 Point들을 반환합니다.
   * 루트 포인트는 제외됩니다.
   *
   * @param streamId 기준 Point가 속한 스트림 ID
   * @param seq      기준 Point의 seq
   * @return 자신 포함 조상 Point 목록 (depth 오름차순, 루트 제외)
   */
  @Transactional(readOnly = true)
  public List<PointDto.Response> getAncestors(Long streamId, int seq) {
    PointEntity point = pointRepository
        .findById(new PointId(streamId, seq))
        .orElseThrow(() -> new NoSuchElementException("Point not found"));

    BranchEntity branch = point.getBranch();

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

  private List<PointEntity> getPointsByPath(
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

}
