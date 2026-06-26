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
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.dto.StreamDto;
import dev.hanju.branchdown.entity.PointEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.PointId;
import dev.hanju.branchdown.repository.PointRepository;
import dev.hanju.branchdown.repository.StreamRepository;

@DisplayName("PointService 통합 테스트")
class PointServiceIntegrationTest extends IntegrationTestBase {

  @Autowired
  private StreamService streamService;

  @Autowired
  private StreamRepository streamRepository;

  @Autowired
  private PointRepository pointRepository;

  private Long streamId;
  private PointEntity rootPoint;

  @BeforeEach
  void setUp() {
    StreamDto.WithRootResponse stream = streamService.createStream();
    streamId = stream.id();
    StreamEntity entity = streamRepository.findById(streamId).orElseThrow();
    rootPoint = entity.getPoints().get(0);
  }

  @Nested
  @DisplayName("pointDown")
  class PointDownTests {

    @Test
    @DisplayName("첫 포인트는 기존 브랜치에 추가된다")
    void firstPointOnSameBranch() {
      PointDto.Response response = streamService.pointDown(streamId, rootPoint.getId().getSeq(), "item1");

      assertThat(response.branchNum()).isEqualTo(0);
      assertThat(response.itemId()).isEqualTo("item1");

      PointEntity saved = pointRepository.findById(new PointId(streamId, response.seq())).orElseThrow();
      assertThat(saved.getDepth()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 포인트에서 두 번째 추가 시 새 브랜치가 생성된다")
    void branchingOnSecondAdd() {
      PointDto.Response first = streamService.pointDown(streamId, rootPoint.getId().getSeq(), "item1");
      PointDto.Response second = streamService.pointDown(streamId, rootPoint.getId().getSeq(), "item2");

      assertThat(first.branchNum()).isEqualTo(0);
      assertThat(second.branchNum()).isEqualTo(1);

      StreamEntity updated = streamRepository.findById(streamId).orElseThrow();
      assertThat(updated.getBranches()).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 포인트에서 예외 발생")
    void notFound() {
      assertThatThrownBy(() -> streamService.pointDown(streamId, 999999, "item"))
          .isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("브랜칭 시나리오")
  class BranchingScenarioTests {

    @Test
    @DisplayName("선형 흐름은 같은 브랜치를 유지한다")
    void linearFlow() {
      PointDto.Response p1 = streamService.pointDown(streamId, rootPoint.getId().getSeq(), "1");
      PointDto.Response p2 = streamService.pointDown(streamId, p1.seq(), "2");
      PointDto.Response p3 = streamService.pointDown(streamId, p2.seq(), "3");

      assertThat(p1.branchNum()).isEqualTo(0);
      assertThat(p2.branchNum()).isEqualTo(0);
      assertThat(p3.branchNum()).isEqualTo(0);

      StreamEntity updated = streamRepository.findById(streamId).orElseThrow();
      assertThat(updated.getBranches()).hasSize(1);
    }

    @Test
    @DisplayName("한 포인트에서 여러 분기 시 각각 새 브랜치가 생성된다")
    void multipleBranches() {
      int rootSeq = rootPoint.getId().getSeq();
      PointDto.Response b0 = streamService.pointDown(streamId, rootSeq, "branch0");
      PointDto.Response b1 = streamService.pointDown(streamId, rootSeq, "branch1");
      PointDto.Response b2 = streamService.pointDown(streamId, rootSeq, "branch2");

      assertThat(b0.branchNum()).isEqualTo(0);
      assertThat(b1.branchNum()).isEqualTo(1);
      assertThat(b2.branchNum()).isEqualTo(2);

      PointEntity updatedRoot = pointRepository.findById(rootPoint.getId()).orElseThrow();
      assertThat(updatedRoot.getChildBranchNums()).containsExactlyInAnyOrder(new int[] { 0, 1, 2 });
    }

    @Test
    @DisplayName("다단계 브랜칭이 올바르게 동작한다")
    void multiLevelBranching() {
      // root -> A -> A1 (branch 0)
      // root -> B (branch 1)
      // A -> A2 (branch 2)
      int rootSeq = rootPoint.getId().getSeq();
      PointDto.Response a = streamService.pointDown(streamId, rootSeq, "A");
      PointDto.Response b = streamService.pointDown(streamId, rootSeq, "B");
      PointDto.Response a1 = streamService.pointDown(streamId, a.seq(), "A1");
      PointDto.Response a2 = streamService.pointDown(streamId, a.seq(), "A2");

      assertThat(a.branchNum()).isEqualTo(0);
      assertThat(b.branchNum()).isEqualTo(1);
      assertThat(a1.branchNum()).isEqualTo(0);
      assertThat(a2.branchNum()).isEqualTo(2);

      StreamEntity updated = streamRepository.findById(streamId).orElseThrow();
      assertThat(updated.getBranches()).hasSize(3);
    }
  }

  @Nested
  @DisplayName("getAncestors")
  class GetAncestorsTests {

    @Test
    @DisplayName("선형 경로에서 조상 목록 반환 (루트 제외, 자신 포함)")
    void linearPath() {
      int rootSeq = rootPoint.getId().getSeq();
      PointDto.Response p1 = streamService.pointDown(streamId, rootSeq, "1");
      PointDto.Response p2 = streamService.pointDown(streamId, p1.seq(), "2");
      PointDto.Response p3 = streamService.pointDown(streamId, p2.seq(), "3");

      List<PointDto.Response> ancestors = streamService.getAncestors(streamId, p3.seq());

      assertThat(ancestors).hasSize(3);
      assertThat(ancestors.get(0).seq()).isEqualTo(p1.seq());
      assertThat(ancestors.get(1).seq()).isEqualTo(p2.seq());
      assertThat(ancestors.get(2).seq()).isEqualTo(p3.seq());
    }

    @Test
    @DisplayName("루트 포인트는 빈 목록을 반환한다")
    void rootReturnsEmpty() {
      List<PointDto.Response> ancestors = streamService.getAncestors(streamId, rootPoint.getId().getSeq());

      assertThat(ancestors).isEmpty();
    }

    @Test
    @DisplayName("분기 후에도 올바른 조상 경로를 반환한다")
    void afterBranching() {
      int rootSeq = rootPoint.getId().getSeq();
      PointDto.Response p1 = streamService.pointDown(streamId, rootSeq, "1");
      streamService.pointDown(streamId, p1.seq(), "2-main"); // branch 0
      PointDto.Response alt = streamService.pointDown(streamId, p1.seq(), "2-alt"); // branch 1
      PointDto.Response alt2 = streamService.pointDown(streamId, alt.seq(), "3-alt");

      List<PointDto.Response> ancestors = streamService.getAncestors(streamId, alt2.seq());

      assertThat(ancestors).hasSize(3);
      assertThat(ancestors.get(0).seq()).isEqualTo(p1.seq());
      assertThat(ancestors.get(1).seq()).isEqualTo(alt.seq());
      assertThat(ancestors.get(2).seq()).isEqualTo(alt2.seq());
    }

    @Test
    @DisplayName("존재하지 않는 포인트에서 예외 발생")
    void notFound() {
      assertThatThrownBy(() -> streamService.getAncestors(streamId, 999999))
          .isInstanceOf(NoSuchElementException.class);
    }
  }
}
