package dev.hanju.branchdown.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.hanju.branchdown.IntegrationTestBase;
import dev.hanju.branchdown.datastructure.PersistentTreeNode;
import dev.hanju.branchdown.datastructure.Tree;
import dev.hanju.branchdown.datastructure.TreeNode;
import dev.hanju.branchdown.entity.BranchEntity;
import dev.hanju.branchdown.entity.PointEntity;
import dev.hanju.branchdown.entity.StreamEntity;
import dev.hanju.branchdown.entity.id.BranchId;
import dev.hanju.branchdown.repository.BranchRepository;
import dev.hanju.branchdown.repository.PointRepository;
import dev.hanju.branchdown.repository.StreamRepository;

@DisplayName("TreeService 통합 테스트")
class TreeServiceIntegrationTest extends IntegrationTestBase {

  @Autowired
  private TreeService treeService;

  @Autowired
  private StreamRepository streamRepository;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private PointRepository pointRepository;

  @Test
  @DisplayName("Tree 저장 시 root는 synthetic root(itemId=null)로 depth 0 Point에 저장된다")
  void saveNewTreeWithSyntheticRoot() {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> child = tree.getRoot().appendChild("child");

    assertThat(child.getBranchNum()).isZero();
    assertThat(tree.getRoot().getChildBranchNums()).containsExactly(0);

    treeService.save(tree);

    assertThat(tree.getId()).isNotNull();
    assertThat(tree.getRoot().getId()).isNotNull();
    assertThat(child.getId()).isNotNull();
    assertThat(tree.getRoot().getTree()).isSameAs(tree);
    assertThat(child.getTree()).isSameAs(tree);
    assertThat(tree.getRoot().getDepth()).isZero();
    assertThat(child.getDepth()).isEqualTo(1);

    PointEntity rootPoint = pointRepository.findById(tree.getRoot().getId()).orElseThrow();
    assertThat(rootPoint.getItemId()).isNull();
    assertThat(rootPoint.getDepth()).isZero();
    assertThat(rootPoint.getBranchNum()).isEqualTo(0);
    assertThat(rootPoint.getChildBranchNums()).containsExactly(0);

    PointEntity childPoint = pointRepository.findById(child.getId()).orElseThrow();
    assertThat(childPoint.getItemId()).isEqualTo("child");
    assertThat(childPoint.getDepth()).isEqualTo(1);
    assertThat(childPoint.getBranchNum()).isEqualTo(0);
  }

  @Test
  @DisplayName("한 parent에 여러 child를 appendChild하면 appendChild 순서대로 branch 번호가 배정된다")
  void saveMultipleChildrenOnSameParent() {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> first = tree.getRoot().appendChild("first");
    PersistentTreeNode<String> second = tree.getRoot().appendChild("second");
    PersistentTreeNode<String> third = tree.getRoot().appendChild("third");

    assertThat(first.getBranchNum()).isEqualTo(0);
    assertThat(second.getBranchNum()).isEqualTo(1);
    assertThat(third.getBranchNum()).isEqualTo(2);
    assertThat(tree.getNextBranchNum()).isEqualTo(3);
    assertThat(tree.getRoot().getChildBranchNums()).containsExactly(0, 1, 2);

    treeService.save(tree);

    assertThat(branchNum(first)).isEqualTo(0);
    assertThat(branchNum(second)).isEqualTo(1);
    assertThat(branchNum(third)).isEqualTo(2);
    assertThat(tree.getRoot().getChildBranchNums()).containsExactly(0, 1, 2);
    assertThat(point(tree.getRoot()).getChildBranchNums()).containsExactly(0, 1, 2);

    StreamEntity stream = streamRepository.findById(tree.getId()).orElseThrow();
    assertThat(stream.getBranches()).hasSize(3);
    assertThat(stream.getNextBranchNum()).isEqualTo(3);
  }

  @Test
  @DisplayName("여러 depth에 누적한 pending appendChild를 한 번에 저장한다")
  void saveNestedPendingAppends() {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> a = tree.getRoot().appendChild("A");
    PersistentTreeNode<String> b = tree.getRoot().appendChild("B");
    PersistentTreeNode<String> a1 = a.appendChild("A1");
    PersistentTreeNode<String> a2 = a.appendChild("A2");
    PersistentTreeNode<String> b1 = b.appendChild("B1");

    assertThat(a.getBranchNum()).isEqualTo(0);
    assertThat(b.getBranchNum()).isEqualTo(1);
    assertThat(a1.getBranchNum()).isEqualTo(0);
    assertThat(a2.getBranchNum()).isEqualTo(2);
    assertThat(b1.getBranchNum()).isEqualTo(1);
    assertThat(tree.getNextBranchNum()).isEqualTo(3);

    treeService.save(tree);

    assertThat(branchNum(a)).isEqualTo(0);
    assertThat(branchNum(b)).isEqualTo(1);
    assertThat(branchNum(a1)).isEqualTo(0);
    assertThat(branchNum(a2)).isEqualTo(2);
    assertThat(branchNum(b1)).isEqualTo(1);

    BranchEntity a2Branch = branchRepository.findById(new BranchId(tree.getId(), 2)).orElseThrow();
    assertThat(a2Branch.getPath()).isEqualTo("0");
  }

  @Test
  @DisplayName("저장된 Stream을 전체 Tree 객체로 복원한다")
  void loadFullTree() {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> a = tree.getRoot().appendChild("A");
    PersistentTreeNode<String> b = tree.getRoot().appendChild("B");
    a.appendChild("A1");
    a.appendChild("A2");
    b.appendChild("B1");
    treeService.save(tree);

    Tree<String> loaded = treeService.loadAllRoute(tree.getId());

    assertThat(loaded.getId()).isEqualTo(tree.getId());
    assertThat(loaded.getRoot().getItem()).isNull();
    assertThat(loaded.getRoot().getTree()).isSameAs(loaded);
    assertThat(loaded.getRoot().getDepth()).isZero();
    assertThat(loaded.getRoot().getChildBranchNums()).containsExactly(0, 1);
    assertThat(itemIds(loaded.getRoot().getChildren())).containsExactly("A", "B");

    PersistentTreeNode<String> loadedA = persistent(loaded.getRoot().getChildren().get(0));
    PersistentTreeNode<String> loadedB = persistent(loaded.getRoot().getChildren().get(1));
    assertThat(loadedA.getChildBranchNums()).containsExactly(0, 2);
    assertThat(loadedA.getTree()).isSameAs(loaded);
    assertThat(loadedA.getDepth()).isEqualTo(1);
    assertThat(itemIds(loadedA.getChildren())).containsExactly("A1", "A2");
    assertThat(itemIds(loadedB.getChildren())).containsExactly("B1");
  }

  @Test
  @DisplayName("최신 route 부분 로드는 미로드 child branch 정보를 유지한다")
  void loadLatestRouteKeepsUnloadedChildBranches() throws InterruptedException {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> a = tree.getRoot().appendChild("A"); Thread.sleep(1);
    tree.getRoot().appendChild("B"); Thread.sleep(1);
    a.appendChild("A1"); Thread.sleep(1);
    a.appendChild("A2");
    treeService.save(tree);

    Tree<String> route = treeService.loadLatestRoute(tree.getId());

    PersistentTreeNode<String> routeA = persistent(route.getRoot().getChildren().get(0));
    assertThat(route.getRoot().getItem()).isNull();
    assertThat(route.getRoot().getChildBranchNums()).containsExactly(0, 1);
    assertThat(itemIds(route.getRoot().getChildren())).containsExactly("A");
    assertThat(routeA.getItem()).isEqualTo("A");
    assertThat(routeA.getChildBranchNums()).containsExactly(0, 2);
    assertThat(itemIds(routeA.getChildren())).containsExactly("A2");
  }

  @Test
  @DisplayName("부분 로드된 노드에서 child branch를 선택해 해당 route를 추가 로드한다")
  void loadSelectedChildRoute() throws InterruptedException {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> a = tree.getRoot().appendChild("A"); Thread.sleep(1);
    PersistentTreeNode<String> b = tree.getRoot().appendChild("B"); Thread.sleep(1);
    a.appendChild("A1"); Thread.sleep(1);
    a.appendChild("A2"); Thread.sleep(1);
    b.appendChild("B1");
    treeService.save(tree);

    Tree<String> route = treeService.loadLatestRoute(tree.getId());
    PersistentTreeNode<String> routeRoot = route.getRoot();

    assertThat(itemIds(routeRoot.getChildren())).containsExactly("B");
    assertThat(routeRoot.getChildBranchNums()).containsExactly(0, 1);

    List<PersistentTreeNode<String>> loadedARoute = treeService.loadChildRoute(routeRoot, 0);

    assertThat(itemIds(loadedARoute)).containsExactly("A", "A1");
    assertThat(itemIds(routeRoot.getChildren())).containsExactly("A", "B");

    PersistentTreeNode<String> routeA = loadedARoute.get(0);
    assertThat(routeA.getChildBranchNums()).containsExactly(0, 2);
    assertThat(itemIds(routeA.getChildren())).containsExactly("A1");

    List<PersistentTreeNode<String>> loadedA2Route = treeService.loadChildRoute(routeA, 2);

    assertThat(itemIds(loadedA2Route)).containsExactly("A2");
    assertThat(itemIds(routeA.getChildren())).containsExactly("A1", "A2");
  }

  @Test
  @DisplayName("부분 로드된 Tree에 appendChild하면 전체 stream 기준 다음 branch 번호를 사용한다")
  void appendChildOnPartialTreeUsesStreamNextBranchNum() throws InterruptedException {
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> a = tree.getRoot().appendChild("A"); Thread.sleep(1);
    tree.getRoot().appendChild("B"); Thread.sleep(1);
    a.appendChild("A1"); Thread.sleep(1);
    a.appendChild("A2");
    treeService.save(tree);

    Tree<String> route = treeService.loadLatestRoute(tree.getId());
    PersistentTreeNode<String> routeRoot = route.getRoot();

    PersistentTreeNode<String> c = routeRoot.appendChild("C");

    assertThat(c.getBranchNum()).isEqualTo(3);
    assertThat(route.getNextBranchNum()).isEqualTo(4);

    treeService.save(route);

    assertThat(branchNum(c)).isEqualTo(3);
    assertThat(point(routeRoot).getChildBranchNums()).containsExactly(0, 1, 3);
    assertThat(streamRepository.findById(route.getId()).orElseThrow().getNextBranchNum()).isEqualTo(4);
  }

  @Test
  @DisplayName("기존 Tree 저장 시 새로 appendChild된 Node만 추가 저장한다")
  void saveExistingTreePersistsOnlyNewNodes() {
    Tree<String> tree = treeService.create();
    tree.getRoot().appendChild("A");
    treeService.save(tree);
    long pointCount = pointRepository.count();

    PersistentTreeNode<String> b = tree.getRoot().appendChild("B");
    treeService.save(tree);

    assertThat(b.getId()).isNotNull();
    assertThat(pointRepository.count()).isEqualTo(pointCount + 1);
    assertThat(tree.getRoot().getChildBranchNums()).containsExactly(0, 1);
    assertThat(point(tree.getRoot()).getChildBranchNums()).containsExactly(0, 1);
  }

  @Test
  @DisplayName("포크가 섞인 다중 appendChild를 일괄 저장해도 branchNum과 branch path가 정확하다")
  void saveInterleavedForksProducesCorrectBranchNumsAndPaths() {
    // root → A(b0), root → B(b1), A → A1(b0), A → A2(b2), B → B1(b1)
    // appendChild 순서와 무관하게 branchNum과 path가 메모리 트리 기준으로 정확해야 한다
    Tree<String> tree = treeService.create();
    PersistentTreeNode<String> a = tree.getRoot().appendChild("A");   // branchNum 0
    PersistentTreeNode<String> b = tree.getRoot().appendChild("B");   // branchNum 1
    PersistentTreeNode<String> a1 = a.appendChild("A1");              // branchNum 0 (부모와 동일)
    PersistentTreeNode<String> a2 = a.appendChild("A2");              // branchNum 2 (새 fork)
    PersistentTreeNode<String> b1 = b.appendChild("B1");              // branchNum 1 (부모와 동일)

    treeService.save(tree);

    assertThat(branchNum(a)).isEqualTo(0);
    assertThat(branchNum(b)).isEqualTo(1);
    assertThat(branchNum(a1)).isEqualTo(0);
    assertThat(branchNum(a2)).isEqualTo(2);
    assertThat(branchNum(b1)).isEqualTo(1);

    // A2는 branch 0에서 분기했으므로 path = "0"
    BranchEntity a2Branch = branchRepository.findById(new BranchId(tree.getId(), 2)).orElseThrow();
    assertThat(a2Branch.getPath()).isEqualTo("0");

    // 부모 point의 childBranchNums도 정확해야 한다
    assertThat(point(a).getChildBranchNums()).containsExactly(0, 2);
    assertThat(point(b).getChildBranchNums()).containsExactly(1);
    assertThat(streamRepository.findById(tree.getId()).orElseThrow().getNextBranchNum()).isEqualTo(3);
  }

  private List<String> itemIds(final List<? extends TreeNode<String>> nodes) {
    return nodes.stream()
        .map(TreeNode::getItem)
        .toList();
  }

  private int branchNum(final PersistentTreeNode<String> node) {
    return point(node).getBranchNum();
  }

  private PointEntity point(final PersistentTreeNode<String> node) {
    return pointRepository.findById(node.getId()).orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private PersistentTreeNode<String> persistent(final TreeNode<String> node) {
    return (PersistentTreeNode<String>) node;
  }
}
