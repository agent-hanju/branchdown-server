package dev.hanju.branchdown.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.hanju.branchdown.IntegrationTestBase;
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

@DisplayName("StreamService 통합 테스트")
class StreamServiceIntegrationTest extends IntegrationTestBase {

  @Autowired
  private StreamService streamService;

  @Autowired
  private StreamRepository streamRepository;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private PointRepository pointRepository;

  @Test
  @DisplayName("스트림 생성 시 초기 브랜치와 synthetic 루트 포인트(itemId=null)를 자동 생성한다")
  void createStream() {
    StreamDto.WithRootResponse response = streamService.createStream();

    assertThat(response.id()).isNotNull();

    StreamEntity stream = streamRepository.findById(response.id()).orElseThrow();
    assertThat(stream.getBranches()).hasSize(1);

    BranchEntity branch = stream.getBranches().get(0);
    assertThat(branch.getBranchNum()).isEqualTo(StreamConstants.INITIAL_BRANCH_NUM);
    assertThat(branch.getPoints()).hasSize(1);

    PointEntity rootPoint = branch.getPoints().get(0);
    assertThat(rootPoint.getDepth()).isEqualTo(StreamConstants.ROOT_POINT_DEPTH);
    assertThat(rootPoint.getItemId()).isNull();
  }

  @Test
  @DisplayName("스트림 조회")
  void getStream() {
    StreamDto.WithRootResponse created = streamService.createStream();

    StreamDto.Response found = streamService.getStream(created.id());

    assertThat(found.id()).isEqualTo(created.id());
  }

  @Test
  @DisplayName("존재하지 않는 스트림 조회 시 예외 발생")
  void getStreamNotFound() {
    assertThatThrownBy(() -> streamService.getStream(999999L))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("스트림 삭제 시 연관 엔티티도 삭제된다")
  void deleteStream() {
    StreamDto.WithRootResponse created = streamService.createStream();
    Long streamId = created.id();
    int branchNum = streamRepository.findById(streamId).orElseThrow()
        .getBranches().get(0).getBranchNum();

    streamService.deleteStream(streamId);

    assertThat(streamRepository.findById(streamId)).isEmpty();
    assertThat(branchRepository.findById(new BranchId(streamId, branchNum))).isEmpty();
  }

  @Test
  @DisplayName("존재하지 않는 스트림 삭제 시 예외 발생")
  void deleteStreamNotFound() {
    assertThatThrownBy(() -> streamService.deleteStream(999999L))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Nested
  @DisplayName("getStreamPoints")
  class GetStreamPointsTests {

    private Long streamId;
    private PointEntity rootPoint;

    @BeforeEach
    void setUp() {
      StreamDto.WithRootResponse stream = streamService.createStream();
      streamId = stream.id();
      StreamEntity entity = streamRepository.findById(streamId).orElseThrow();
      rootPoint = entity.getBranches().get(0).getPoints().get(0);
    }

    @Test
    @DisplayName("포인트 목록 반환")
    void getStreamPoints() {
      PointEntity point = PointEntity.builder()
          .branch(rootPoint.getBranch())
          .depth(1)
          .itemId("item1")
          .childBranchNums(new int[0])
          .build();
      pointRepository.save(point);
      rootPoint.addChildBranchNum(rootPoint.getBranch().getBranchNum());
      pointRepository.save(rootPoint);

      List<PointDto.Response> result = streamService.getStreamPoints(streamId);

      assertThat(result).hasSizeGreaterThanOrEqualTo(1);
      assertThat(result.get(0).branchNum()).isEqualTo(StreamConstants.INITIAL_BRANCH_NUM);
    }

    @Test
    @DisplayName("존재하지 않는 스트림 조회 시 예외 발생")
    void notFound() {
      assertThatThrownBy(() -> streamService.getStreamPoints(999999L))
          .isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("getBranchMessages")
  class GetBranchMessagesTests {

    private Long streamId;
    private BranchEntity branch;

    @BeforeEach
    void setUp() {
      StreamDto.WithRootResponse stream = streamService.createStream();
      streamId = stream.id();
      branch = streamRepository.findById(streamId).orElseThrow().getBranches().get(0);
    }

    @Test
    @DisplayName("브랜치 포인트 목록 반환")
    void getBranchMessages() {
      List<PointDto.Response> result = streamService.getBranchMessages(
          streamId, StreamConstants.INITIAL_BRANCH_NUM, -1);

      assertThat(result).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 브랜치 조회 시 예외 발생")
    void notFound() {
      assertThatThrownBy(() -> streamService.getBranchMessages(streamId, 99, 0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("depth 이후 포인트만 조회")
    void withDepth() {
      PointEntity point1 = PointEntity.builder()
          .branch(branch)
          .depth(1)
          .itemId("item1")
          .childBranchNums(new int[0])
          .build();
      PointEntity point2 = PointEntity.builder()
          .branch(branch)
          .depth(2)
          .itemId("item2")
          .childBranchNums(new int[0])
          .build();
      pointRepository.save(point1);
      pointRepository.save(point2);

      List<PointDto.Response> result = streamService.getBranchMessages(
          streamId, StreamConstants.INITIAL_BRANCH_NUM, 1);

      assertThat(result).allMatch(p -> p.id().equals(point2.getId()));
    }
  }
}
