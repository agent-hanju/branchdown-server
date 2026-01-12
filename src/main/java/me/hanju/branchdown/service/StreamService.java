package me.hanju.branchdown.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.branchdown.constant.StreamConstants;
import me.hanju.branchdown.dto.PointDto;
import me.hanju.branchdown.dto.StreamDto;
import me.hanju.branchdown.entity.BranchEntity;
import me.hanju.branchdown.entity.PointEntity;
import me.hanju.branchdown.entity.StreamEntity;
import me.hanju.branchdown.entity.id.BranchId;
import me.hanju.branchdown.repository.BranchRepository;
import me.hanju.branchdown.repository.PointRepository;
import me.hanju.branchdown.repository.StreamRepository;
import me.hanju.branchdown.util.PathUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

  private static final String STREAM_NOT_FOUND = "stream not found";

  private final StreamRepository streamRepository;
  private final BranchRepository branchRepository;
  private final PointRepository pointRepository;

  /**
   * 스트림 생성
   *
   * @return 생성된 StreamDto.Response
   */
  @Transactional
  public StreamDto.Response createStream() {
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

    // 3. 스트림의 루트 포인트 생성
    final PointEntity rootPoint = PointEntity.builder()
        .branch(initialBranch)
        .depth(StreamConstants.ROOT_POINT_DEPTH)
        .build();
    pointRepository.save(rootPoint);

    initialBranch.addPoint(rootPoint);

    return newStream.toResponse();
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
}
