package dev.hanju.branchdown.datastructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** 트리 노드 자료구조 구현체 */
public class TreeNode<T> {

  private final T item;
  
  protected TreeNode<T> parent;
  protected int branchNum;
  protected final List<TreeNode<T>> children = new ArrayList<>();

  protected int depth;  // read-only
  private int localNextBranchNum; // read-only

  /**
   * 독립 트리 노드를 생성합니다.
   *
   * @param item 이 노드가 가지는 item
   * @param parent 부모 노드
   * @param branchNum 이 노드가 속한 branch 번호
   */
  public TreeNode(final T item, final TreeNode<T> parent, final int branchNum) {
    this.item = item;
    this.parent = parent;
    this.branchNum = branchNum;
    this.depth = parent == null ? 0 : parent.getDepth() + 1;
    this.localNextBranchNum = branchNum + 1;
  }

  /** 이 노드가 가리키는 item을 반환합니다. */
  public T getItem() { return item; }

  /** 부모 노드를 반환합니다. root라면 null입니다. */
  public TreeNode<T> getParent() { return parent; }

    /** 이 노드가 속한 branch 번호를 반환합니다. */
  public int getBranchNum() { return branchNum; }

  /** 현재 노드가 직접 가진 자식 노드를 반환합니다. 규칙에 따라 branchNum 오름차순으로 반환됩니다.  */
  public List<TreeNode<T>> getChildren() { return children.stream().toList(); }

  /** root부터의 depth를 반환합니다. root는 0입니다. */
  public int getDepth() { return depth; }

  /** 현재 노드부터 메모리에 로드된 subtree를 DFS pre-order로 순회합니다. */
  public Iterator<TreeNode<T>> traversal() {
    return new Iterator<>() {
      private final List<TreeNode<T>> stack = new ArrayList<>(List.of(TreeNode.this));

      @Override
      public boolean hasNext() {
        return !stack.isEmpty();
      }

      @Override
      public TreeNode<T> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        final TreeNode<T> node = stack.remove(stack.size() - 1);
        for (int i = node.children.size() - 1; i >= 0; i -= 1) {
          stack.add(node.children.get(i));
        }
        return node;
      }
    };
  }

  /**
   * root부터 부모까지 이어지는 branchNum 경로를 int 배열로 반환합니다.(자기 자신의 branchNum은 제외)
   *
   * @return root -> parent 방향의 중복 제거된 branchNum 경로
   */
  public int[] branchNumPath() {
    final List<Integer> raw = new ArrayList<>();
    TreeNode<T> current = parent;
    while (current != null) {
      raw.add(current.branchNum);
      current = current.parent;
    }
    Collections.reverse(raw);

    final List<Integer> deduped = new ArrayList<>();
    for (final int num : raw) {
      if (deduped.isEmpty() || deduped.get(deduped.size() - 1) != num) {
        deduped.add(num);
      }
    }
    return deduped.stream().mapToInt(Integer::intValue).toArray();
  }

  /**
   * 현재 노드 아래에 새 자식 노드를 추가합니다.
   *
   * @param item 새 자식 노드가 가질 item
   * @return 새로 추가된 자식 노드
   */
  public TreeNode<T> appendChild(final T item) {
    final int childBranchNum = hasChildBranches() ? issueNextBranchNum() : branchNum;
    final int index = searchChildIndex(childBranchNum);
    final TreeNode<T> child = createChild(item, childBranchNum);
    children.add(-index - 1, child);
    return child;
  }

  /**
   * parent가 없는 노드를 현재 노드의 자식으로 붙입니다.
   *
   * @param treeNode parent가 없는 노드
   * @return 현재 노드 아래로 붙은 subtree root 노드
   */
  public TreeNode<T> appendChild(final TreeNode<T> treeNode) {
    Objects.requireNonNull(treeNode, "subtree must not be null");
    validateAppendSubtree(treeNode);

    // 현재 노드의 첫 child라면 branch를 계승하고, 아니면 현재 root allocator의 다음 branch를 쓴다.
    final boolean inheritsParentBranch = !hasChildBranches();
    final int targetNextBranchNum = peekNextBranchNum();
    final int oldRootBranchNum = treeNode.getBranchNum();
    final int newRootBranchNum = inheritsParentBranch ? branchNum : targetNextBranchNum;
    final int oldRootDepth = treeNode.getDepth();
    final int newRootDepth = depth + 1;
    final int depthDelta = newRootDepth - oldRootDepth;
    int maxRebasedBranchNum = newRootBranchNum;

    treeNode.parent = this;
    final Iterator<TreeNode<T>> traversal = treeNode.traversal();
    while (traversal.hasNext()) {
      final TreeNode<T> node = traversal.next();

      // subtree 내부 연결은 유지하되, depth/branchNum만 target 기준으로 이동한다.
      maxRebasedBranchNum = Math.max(maxRebasedBranchNum, node.rebaseSelf(
          oldRootBranchNum,
          newRootBranchNum,
          targetNextBranchNum,
          inheritsParentBranch,
          depthDelta));

      for (final TreeNode<T> child : node.children) {
        child.parent = node;
      }
    }

    // subtree가 차지한 branch 범위만큼 target root allocator를 앞으로 민다.
    syncNextBranchNum(maxRebasedBranchNum + 1);

    final int index = searchChildIndex(treeNode.getBranchNum());
    children.add(-index - 1, treeNode);
    return treeNode;
  }

  /** item append 시 사용할 자식 노드 인스턴스를 생성한다 */
  protected TreeNode<T> createChild(final T item, final int branchNum) {
    return new TreeNode<>(item, this, branchNum);
  }

  /** 현재 노드가 자식 노드를 하나 이상 갖고 있으면 true를 반환합니다. */
  protected boolean hasChildBranches() {
    return !children.isEmpty();
  }

  /**
   * subtree append가 가능한 root인지 검사합니다.
   *
   * @param treeNode 붙일 subtree root
   * @throws IllegalArgumentException subtree root가 이미 parent를 가진 경우
   */
  protected void validateAppendSubtree(final TreeNode<T> treeNode) {
    treeNode.validateAsSubtreeRoot();
  }

  /**
   * 이 노드가 다른 노드 아래에 subtree root로 붙을 수 있는지 검사합니다.
   * subclass는 자기 상태에 맞는 detached 조건을 추가할 수 있습니다.
   */
  protected void validateAsSubtreeRoot() {
    if (getParent() != null) {
      throw new IllegalArgumentException("subtree root must not have a parent");
    }
  }

  /**
   * subtree attach 중 현재 노드의 depth/branchNum을 target 기준으로 이동합니다.
   *
   * @return rebase 후 현재 노드가 차지하는 최대 branchNum
   */
  protected int rebaseSelf(
      final int oldRootBranchNum,
      final int newRootBranchNum,
      final int targetNextBranchNum,
      final boolean inheritsParentBranch,
      final int depthDelta) {
    depth += depthDelta;
    branchNum = rebaseBranchNum(
        branchNum,
        oldRootBranchNum,
        newRootBranchNum,
        targetNextBranchNum,
        inheritsParentBranch);
    return branchNum;
  }

  /**
   * 현재 노드가 속한 연결 컴포넌트의 root 노드를 반환합니다.
   *
   * @return parent가 없는 최상위 노드
   */
  protected TreeNode<T> getRootNode() {
    TreeNode<T> current = this;
    while (current.parent != null) {
      current = current.parent;
    }
    return current;
  }

  /**
   * 다음으로 발급될 branchNum을 소비하지 않고 조회합니다.
   *
   * @return 다음 branchNum 후보
   */
  protected int peekNextBranchNum() {
    return getRootNode().localNextBranchNum;
  }

  /**
   * 새 child branch가 필요할 때 branchNum을 하나 발급합니다.
   *
   * @return 발급된 branchNum
   */
  protected int issueNextBranchNum() {
    final TreeNode<T> root = getRootNode();
    final int issued = root.localNextBranchNum;
    root.localNextBranchNum += 1;
    return issued;
  }

  /**
   * 현재 root allocator가 최소한 nextBranchNum을 가리키도록 동기화합니다.
   *
   * @param nextBranchNum allocator가 최소한 가리켜야 하는 다음 branchNum
   */
  protected void syncNextBranchNum(final int nextBranchNum) {
    final TreeNode<T> root = getRootNode();
    root.localNextBranchNum = Math.max(root.localNextBranchNum, nextBranchNum);
  }

  /**
   * 로딩된 자식 목록에서 branchNum에 해당하는 위치를 이진 탐색합니다.
   * <p>
   * 반환 규칙은 {@link Collections#binarySearch(List, Object)}와 같습니다. 이미 child가 있으면
   * children 안의 index를 반환하고, 없으면 정렬 순서를 유지하며 삽입될 위치를
   * {@code -(insertionPoint + 1)}로 반환합니다.
   * </p>
   *
   * @param branchNum 찾을 child branchNum
   * @return child의 index, 없으면 {@code -(insertionPoint + 1)}
   */
  protected int searchChildIndex(final int branchNum) {
    int low = 0;
    int high = children.size() - 1;
    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final int midBranchNum = children.get(mid).getBranchNum();
      if (midBranchNum < branchNum) {
        low = mid + 1;
      } else if (midBranchNum > branchNum) {
        high = mid - 1;
      } else {
        return mid;
      }
    }
    return -(low + 1);
  }

  /**
   * detached subtree의 기존 branchNum을 target 위치의 branchNum으로 변환합니다.
   */
  protected static int rebaseBranchNum(
      final int oldBranchNum,
      final int oldRootBranchNum,
      final int newRootBranchNum,
      final int targetNextBranchNum,
      final boolean inheritsParentBranch) {
    if (oldBranchNum == oldRootBranchNum) {
      return newRootBranchNum;
    }
    if (inheritsParentBranch) {
      return oldBranchNum + targetNextBranchNum - oldRootBranchNum - 1;
    }
    return oldBranchNum + targetNextBranchNum - oldRootBranchNum;
  }
}
