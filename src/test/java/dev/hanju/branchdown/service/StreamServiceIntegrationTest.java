package dev.hanju.branchdown.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.hanju.branchdown.IntegrationTestBase;
import dev.hanju.branchdown.constant.BranchdownConstants;
import dev.hanju.branchdown.dto.StreamDto;
import dev.hanju.branchdown.entity.BranchEntity;
import dev.hanju.branchdown.entity.PointEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.BranchId;
import dev.hanju.branchdown.repository.BranchRepository;
import dev.hanju.branchdown.repository.StreamRepository;

@DisplayName("StreamService 통합 테스트")
class StreamServiceIntegrationTest extends IntegrationTestBase {

  @Autowired
  private StreamService streamService;

  @Autowired
  private StreamRepository streamRepository;

  @Autowired
  private BranchRepository branchRepository;

  @Test
  @DisplayName("스트림 생성 시 초기 브랜치와 synthetic 루트 포인트(itemId=null)를 자동 생성한다")
  void createStream() {
    StreamDto.WithRootResponse response = streamService.createStream();

    assertThat(response.id()).isNotNull();

    StreamEntity stream = streamRepository.findById(response.id()).orElseThrow();
    assertThat(stream.getBranches()).hasSize(1);

    BranchEntity branch = stream.getBranches().get(0);
    assertThat(branch.getBranchNum()).isEqualTo(BranchdownConstants.INITIAL_BRANCH_NUM);

    PointEntity rootPoint = stream.getPoints().get(0);
    assertThat(rootPoint.getDepth()).isEqualTo(BranchdownConstants.ROOT_DEPTH);
    assertThat(rootPoint.getItemId()).isNull();
  }

  @Test
  @DisplayName("스트림 조회 시 root 포인트를 포함한다")
  void getStream() {
    StreamDto.WithRootResponse created = streamService.createStream();

    StreamDto.WithRootResponse found = streamService.getStream(created.id());

    assertThat(found.id()).isEqualTo(created.id());
    assertThat(found.root()).isNotNull();
    assertThat(found.root().itemId()).isNull();
    assertThat(found.root().seq()).isEqualTo(created.root().seq());
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
    private int rootSeq;

    @BeforeEach
    void setUp() {
      StreamDto.WithRootResponse stream = streamService.createStream();
      streamId = stream.id();
      rootSeq = stream.root().seq();
    }

    @Test
    @DisplayName("포인트 목록 반환")
    void getStreamPoints() {
      streamService.pointDown(streamId, rootSeq, "item1");

      StreamDto.Internal result = streamService.getLatestBranchPoints(streamId);

      assertThat(result.points()).hasSizeGreaterThanOrEqualTo(1);
      assertThat(result.points().get(0).branchNum()).isEqualTo(BranchdownConstants.INITIAL_BRANCH_NUM);
    }

    @Test
    @DisplayName("nextSeq와 nextBranchNum을 함께 반환한다")
    void includesMetadata() {
      StreamDto.Internal result = streamService.getLatestBranchPoints(streamId);

      assertThat(result.nextSeq()).isEqualTo(1);
      assertThat(result.nextBranchNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 스트림 조회 시 예외 발생")
    void notFound() {
      assertThatThrownBy(() -> streamService.getLatestBranchPoints(999999L))
          .isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("getBranchMessages")
  class GetBranchMessagesTests {

    private Long streamId;
    private int rootSeq;

    @BeforeEach
    void setUp() {
      StreamDto.WithRootResponse stream = streamService.createStream();
      streamId = stream.id();
      rootSeq = stream.root().seq();
    }

    @Test
    @DisplayName("브랜치 포인트 목록 반환")
    void getBranchMessages() {
      StreamDto.Internal result = streamService.getBranchPoints(
          streamId, BranchdownConstants.INITIAL_BRANCH_NUM, -1);

      assertThat(result.points()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 브랜치 조회 시 예외 발생")
    void notFound() {
      assertThatThrownBy(() -> streamService.getBranchPoints(streamId, 99, 0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("depth 이후 포인트만 조회")
    void withDepth() {
      var p1 = streamService.pointDown(streamId, rootSeq, "item1");
      var p2 = streamService.pointDown(streamId, p1.seq(), "item2");

      StreamDto.Internal result = streamService.getBranchPoints(
          streamId, BranchdownConstants.INITIAL_BRANCH_NUM, 1);

      assertThat(result.points()).allMatch(p -> p.seq() == p2.seq());
    }
  }
}
