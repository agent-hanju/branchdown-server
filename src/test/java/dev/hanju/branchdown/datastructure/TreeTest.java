package dev.hanju.branchdown.datastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tree 단위 테스트")
class TreeTest {

  @Test
  @DisplayName("무인자 Tree()의 root는 item 없는 구조적 앵커다")
  void newTreeHasSyntheticRoot() {
    Tree<String> tree = new Tree<>();

    assertThat(tree.getRoot().getItem()).isNull();
    assertThat(tree.getRoot().getParent()).isNull();
    assertThat(tree.getRoot().getBranchNum()).isZero();
    assertThat(tree.getRoot().getDepth()).isZero();
    assertThat(tree.getNextBranchNum()).isEqualTo(1);
  }

  @Test
  @DisplayName("root에 item을 담으려 하면 거부한다")
  void rejectsRootWithItem() {
    PersistentTreeNode<String> root = new PersistentTreeNode<>(null, "x", null, 0, null);

    assertThatThrownBy(() -> new Tree<>(null, root, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("root must not carry an item");
  }

  @Test
  @DisplayName("Tree에 붙지 않은 순수 노드도 자체 branchNum 규칙으로 child를 appendChild할 수 있다")
  void appendsChildOnDetachedNode() {
    TreeNode<String> root = new TreeNode<>("root", null, 0);

    TreeNode<String> first = root.appendChild("first");
    TreeNode<String> second = root.appendChild("second");
    TreeNode<String> firstMain = first.appendChild("first-main");
    TreeNode<String> firstFork = first.appendChild("first-fork");

    assertThat(first.getBranchNum()).isZero();
    assertThat(second.getBranchNum()).isEqualTo(1);
    assertThat(firstMain.getBranchNum()).isZero();
    assertThat(firstFork.getBranchNum()).isEqualTo(2);
    assertThat(firstFork.getDepth()).isEqualTo(2);
  }

  @Test
  @DisplayName("traversal은 로드된 subtree를 DFS pre-order로 순회한다")
  void traversalWalksLoadedSubtreeInDfsPreOrder() {
    TreeNode<String> root = new TreeNode<>("root", null, 0);
    TreeNode<String> first = root.appendChild("first");
    TreeNode<String> second = root.appendChild("second");
    TreeNode<String> firstMain = first.appendChild("first-main");
    TreeNode<String> firstFork = first.appendChild("first-fork");
    TreeNode<String> secondMain = second.appendChild("second-main");

    final Iterator<TreeNode<String>> traversal = root.traversal();
    final List<TreeNode<String>> visited = new ArrayList<>();
    while (traversal.hasNext()) {
      visited.add(traversal.next());
    }

    assertThat(visited).containsExactly(root, first, firstMain, firstFork, second, secondMain);
  }

  @Test
  @DisplayName("순수 appendChild는 child 없는 parent에 subtree를 붙일 때 parent branch를 계승한다")
  void appendChildWithSubtreeInheritsParentBranchWhenParentHasNoChild() {
    TreeNode<String> hostRoot = new TreeNode<>("host-root", null, 0);
    TreeNode<String> target = hostRoot.appendChild("target");
    hostRoot.appendChild("sibling");
    TreeNode<String> subtree = new TreeNode<>("subtree", null, 0);
    TreeNode<String> main = subtree.appendChild("main");
    TreeNode<String> fork = subtree.appendChild("fork");

    target.appendChild(subtree);

    assertThat(subtree.getParent()).isSameAs(target);
    assertThat(subtree.getBranchNum()).isZero();
    assertThat(main.getBranchNum()).isZero();
    assertThat(fork.getBranchNum()).isEqualTo(2);
    assertThat(subtree.getDepth()).isEqualTo(2);
    assertThat(main.getDepth()).isEqualTo(3);
    assertThat(branchNums(target.getChildren())).containsExactly(0);
  }

  @Test
  @DisplayName("순수 appendChild는 child 있는 parent에 subtree를 붙일 때 target nextBranchNum 기준으로 이동한다")
  void appendChildWithSubtreeUsesNextBranchNumWhenParentHasChild() {
    TreeNode<String> hostRoot = new TreeNode<>("host-root", null, 0);
    hostRoot.appendChild("existing");
    TreeNode<String> subtree = new TreeNode<>("subtree", null, 0);
    TreeNode<String> main = subtree.appendChild("main");
    TreeNode<String> fork = subtree.appendChild("fork");

    hostRoot.appendChild(subtree);
    TreeNode<String> next = hostRoot.appendChild("next");

    assertThat(subtree.getBranchNum()).isEqualTo(1);
    assertThat(main.getBranchNum()).isEqualTo(1);
    assertThat(fork.getBranchNum()).isEqualTo(2);
    assertThat(next.getBranchNum()).isEqualTo(3);
    assertThat(branchNums(hostRoot.getChildren())).containsExactly(0, 1, 3);
  }

  @Test
  @DisplayName("순수 appendChild는 0이 아닌 branchNum에서 시작한 subtree도 상대 branch 위치를 유지하며 붙인다")
  void appendChildWithSubtreeRebasesNonZeroRootBranchNum() {
    TreeNode<String> hostRoot = new TreeNode<>("host-root", null, 0);
    hostRoot.appendChild("existing");
    TreeNode<String> subtree = new TreeNode<>("subtree", null, 5);
    TreeNode<String> main = subtree.appendChild("main");
    TreeNode<String> fork = subtree.appendChild("fork");
    TreeNode<String> nestedFork = fork.appendChild("nested-fork");

    hostRoot.appendChild(subtree);
    TreeNode<String> next = hostRoot.appendChild("next");

    assertThat(subtree.getBranchNum()).isEqualTo(1);
    assertThat(main.getBranchNum()).isEqualTo(1);
    assertThat(fork.getBranchNum()).isEqualTo(2);
    assertThat(nestedFork.getBranchNum()).isEqualTo(2);
    assertThat(nestedFork.getDepth()).isEqualTo(3);
    assertThat(next.getBranchNum()).isEqualTo(3);
    assertThat(branchNums(hostRoot.getChildren())).containsExactly(0, 1, 3);
  }

  @Test
  @DisplayName("순수 appendChild는 child 없는 parent에 0이 아닌 branchNum subtree를 붙이면 parent branch를 계승한다")
  void appendChildWithNonZeroSubtreeRootInheritsParentBranchWhenParentHasNoChild() {
    TreeNode<String> hostRoot = new TreeNode<>("host-root", null, 0);
    TreeNode<String> target = hostRoot.appendChild("target");
    hostRoot.appendChild("sibling");
    TreeNode<String> subtree = new TreeNode<>("subtree", null, 5);
    TreeNode<String> main = subtree.appendChild("main");
    TreeNode<String> fork = subtree.appendChild("fork");

    target.appendChild(subtree);

    assertThat(subtree.getBranchNum()).isZero();
    assertThat(main.getBranchNum()).isZero();
    assertThat(fork.getBranchNum()).isEqualTo(2);
    assertThat(subtree.getDepth()).isEqualTo(2);
    assertThat(branchNums(target.getChildren())).containsExactly(0);
  }

  @Test
  @DisplayName("순수 appendChild는 parent가 이미 있는 subtree root를 거부한다")
  void appendChildRejectsSubtreeWithParent() {
    TreeNode<String> parent = new TreeNode<>("parent", null, 0);
    TreeNode<String> child = parent.appendChild("child");

    assertThatThrownBy(() -> parent.appendChild(child))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not have a parent");
  }

  @Test
  @DisplayName("순수 appendChild도 persistent subtree root의 detached 조건을 존중한다")
  void appendChildRespectsPersistentSubtreeRootState() {
    TreeNode<String> parent = new TreeNode<>("parent", null, 0);
    Tree<String> tree = new Tree<>();
    PersistentTreeNode<String> persisted = new PersistentTreeNode<>(1L, "persisted", null, 0, null);

    assertThatThrownBy(() -> parent.appendChild(tree.getRoot()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not belong to a tree");
    assertThatThrownBy(() -> parent.appendChild(persisted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("persisted subtree root");
  }

  @Test
  @DisplayName("persistent appendChild는 tree나 id가 있는 subtree root를 거부한다")
  void persistentAppendChildRejectsNonDetachedRoot() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, "parent", null, 0, null);
    Tree<String> tree = new Tree<>();
    PersistentTreeNode<String> persisted = new PersistentTreeNode<>(1L, "persisted", null, 0, null);

    assertThatThrownBy(() -> parent.appendChild(tree.getRoot()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not belong to a tree");
    assertThatThrownBy(() -> parent.appendChild(persisted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("persisted subtree root");
  }

  @Test
  @DisplayName("persistent appendChild는 순수 TreeNode subtree를 거부한다")
  void persistentAppendChildRejectsPureSubtreeRoot() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, "parent", null, 0, null);
    TreeNode<String> pureSubtree = new TreeNode<>("pure", null, 0);

    assertThatThrownBy(() -> parent.appendChild(pureSubtree))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PersistentTreeNode");
  }

  @Test
  @DisplayName("loadChild는 childBranchNums에 없는 child를 거부한다")
  void loadChildRejectsUnknownBranchNum() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, null, null, 0, new int[] {0});
    PersistentTreeNode<String> child = new PersistentTreeNode<>(null, "child", parent, 1, null);

    assertThatThrownBy(() -> parent.loadChild(child))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Child branch not found: 1");
  }

  @Test
  @DisplayName("loadChild는 branchNum 순서로 child를 로딩한다")
  void loadChildKeepsChildrenSortedByBranchNum() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, null, null, 0, new int[] {0, 2});
    PersistentTreeNode<String> later = new PersistentTreeNode<>(null, "later", parent, 2, null);
    PersistentTreeNode<String> first = new PersistentTreeNode<>(null, "first", parent, 0, null);

    parent.loadChild(later);
    parent.loadChild(first);

    assertThat(branchNums(parent.getChildren())).containsExactly(0, 2);
  }

  @Test
  @DisplayName("getChild는 없는 childBranchNum이면 인자 오류를 낸다")
  void getChildRejectsUnknownBranchNum() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, null, null, 0, new int[] {0});

    assertThat(parent.hasChild(1)).isFalse();
    assertThatThrownBy(() -> parent.getChild(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Child branch not found: 1");
  }

  @Test
  @DisplayName("getChild는 childBranchNum이 있지만 로드되지 않았으면 상태 오류를 낸다")
  void getChildRejectsUnloadedChild() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, null, null, 0, new int[] {0});

    assertThat(parent.hasChild(0)).isTrue();
    assertThat(parent.isChildLoaded(0)).isFalse();
    assertThatThrownBy(() -> parent.getChild(0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Child branch is not loaded: 0");
  }

  @Test
  @DisplayName("getChild는 로드된 child를 branchNum으로 반환한다")
  void getChildReturnsLoadedChild() {
    PersistentTreeNode<String> parent = new PersistentTreeNode<>(null, null, null, 0, new int[] {0});
    PersistentTreeNode<String> child = new PersistentTreeNode<>(null, "child", parent, 0, null);

    parent.loadChild(child);

    assertThat(parent.isChildLoaded(0)).isTrue();
    assertThat(parent.getChild(0)).isSameAs(child);
  }

  private List<Integer> branchNums(final List<TreeNode<String>> nodes) {
    return nodes.stream()
        .map(TreeNode::getBranchNum)
        .toList();
  }
}
