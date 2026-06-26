package dev.hanju.branchdown.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.hanju.branchdown.BranchdownLoadResult;
import dev.hanju.branchdown.BranchdownTreeNode;
import dev.hanju.branchdown.IntegrationTestBase;
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.dto.StreamDto;

@DisplayName("TreeService 통합 테스트")
class TreeServiceIntegrationTest extends IntegrationTestBase {

  @Autowired
  private TreeService treeService;

  @Autowired
  private StreamService streamService;

  private Long streamId;
  private PointDto.Response rootPoint;

  @BeforeEach
  void setUp() {
    StreamDto.WithRootResponse stream = streamService.createStream();
    streamId = stream.id();
    rootPoint = stream.root();
  }

  @Nested
  @DisplayName("loadLatestRoute")
  class LoadLatestRouteTests {

    @Test
    @DisplayName("root만 있을 때 노드 1개와 초기 메타데이터를 반환한다")
    void onlyRoot() {
      BranchdownLoadResult<String> result = treeService.loadLatestRoute(streamId);

      assertThat(result.route()).hasSize(1);
      assertThat(result.nextSeq()).isEqualTo(1);
      assertThat(result.nextBranchNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("선형 추가 후 모든 노드를 순서대로 반환한다")
    void linearNodes() {
      PointDto.Response p1 = streamService.pointDown(streamId, rootPoint.seq(), "item1");
      PointDto.Response p2 = streamService.pointDown(streamId, p1.seq(), "item2");

      BranchdownLoadResult<String> result = treeService.loadLatestRoute(streamId);

      assertThat(result.route()).hasSize(3);
      assertThat(result.route().get(1).getItem()).isEqualTo("item1");
      assertThat(result.route().get(2).getItem()).isEqualTo("item2");
      assertThat(result.nextSeq()).isEqualTo(3);
    }

    @Test
    @DisplayName("분기 시 가장 최신 브랜치의 경로를 반환한다")
    void afterBranching() {
      PointDto.Response p1 = streamService.pointDown(streamId, rootPoint.seq(), "main");
      streamService.pointDown(streamId, p1.seq(), "branch0");
      streamService.pointDown(streamId, p1.seq(), "branch1");

      BranchdownLoadResult<String> result = treeService.loadLatestRoute(streamId);

      assertThat(result.route().stream().map(BranchdownTreeNode::getItem).filter(id -> id != null).toList())
          .containsExactly("main", "branch1");
    }
  }

  @Nested
  @DisplayName("loadRoute")
  class LoadRouteTests {

    @Test
    @DisplayName("지정한 branchNum의 경로를 반환한다")
    void specificBranch() {
      PointDto.Response p1 = streamService.pointDown(streamId, rootPoint.seq(), "main");
      streamService.pointDown(streamId, p1.seq(), "branch0");
      streamService.pointDown(streamId, p1.seq(), "branch1");

      BranchdownLoadResult<String> result = treeService.loadRoute(streamId, 0);

      assertThat(result.route().stream().map(BranchdownTreeNode::getItem).filter(id -> id != null).toList())
          .containsExactly("main", "branch0");
    }

    @Test
    @DisplayName("nextSeq와 nextBranchNum이 스트림 전체 기준으로 반환된다")
    void metadataIsStreamScoped() {
      streamService.pointDown(streamId, rootPoint.seq(), "item1");
      streamService.pointDown(streamId, rootPoint.seq(), "item2");

      BranchdownLoadResult<String> result = treeService.loadRoute(streamId, 0);

      assertThat(result.nextSeq()).isEqualTo(3);
      assertThat(result.nextBranchNum()).isEqualTo(2);
    }
  }
}
