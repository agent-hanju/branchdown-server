package me.hanju.branchdown.datastructure;

import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

/**
 * 저장 및 부분 로딩 상태를 함께 가진 TreeNode입니다.
 * <p>
 * {@link TreeNode}가 순수한 parent/children 구조를 담당하고, 이 클래스는 DB Point ID,
 * 소속 Tree, 저장소 기준 child branch 목록을 추가로 관리합니다.
 * </p>
 */
public class PersistentTreeNode<T> extends TreeNode<T> {

  private Tree<T> tree;
  private int[] childBranchNums = new int[0];
  private Long id;
  private final Instant createdAt = Instant.now();

  /**
   * 저장 가능한 트리 노드를 생성합니다.
   *
   * @param id 저장된 Point ID, 새 노드라면 null
   * @param item 이 노드가 가지는 item
   * @param parent 부모 노드
   * @param branchNum 이 노드가 속한 branch 번호
   * @param childBranchNums 저장소 기준 전체 child branchNum 배열
   */
  public PersistentTreeNode(
      final Long id,
      final T item,
      final PersistentTreeNode<T> parent,
      final int branchNum,
      final int[] childBranchNums) {
    super(item, parent, branchNum);
    this.id = id;
    this.tree = parent == null ? null : parent.getTree();
    if (childBranchNums != null) {
      this.childBranchNums = Arrays.copyOf(childBranchNums, childBranchNums.length);
    }

    int maxBranchNum = branchNum;
    for (final int childBranchNum : this.childBranchNums) {
      maxBranchNum = Math.max(maxBranchNum, childBranchNum);
    }
    syncNextBranchNum(maxBranchNum + 1);
  }

  /** 저장된 Point ID를 반환합니다. 저장 전 노드라면 null입니다. */
  public Long getId() { return id; }

  /** 이 노드가 메모리에서 생성된 시각을 반환합니다. */
  public Instant getCreatedAt() { return createdAt; }

  /** 저장된 Point ID가 있으면 true를 반환합니다. */
  public boolean isPersisted() { return id != null; }

  /** 이 노드가 속한 Tree를 반환합니다. 아직 Tree에 붙지 않은 독립 노드라면 null일 수 있습니다. */
  public Tree<T> getTree() { return tree; }

  /** 부모 노드를 PersistentTreeNode 타입으로 반환합니다. root라면 null입니다. */
  public PersistentTreeNode<T> getPersistentParent() {
    return parent == null ? null : requirePersistentNode(parent);
  }

  /** 이 노드 아래로 이어지는 모든 자식 branchNum을 반환합니다. */
  public int[] getChildBranchNums() { return Arrays.copyOf(childBranchNums, childBranchNums.length); }

  /** 해당 branchNum을 가진 자식 브랜치가 있으면 true를 반환합니다. */
  public boolean hasChild(final int branchNum) { return Arrays.binarySearch(childBranchNums, branchNum) >= 0; }

  /**
   * 해당 branchNum의 자식 노드가 현재 메모리에 로드되어 있으면 true를 반환합니다.
   *
   * @throws IllegalArgumentException 해당 branchNum이 자식 브랜치가 아닌 경우
   */
  public boolean isChildLoaded(final int branchNum) {
    ensureChildBranchExists(branchNum);
    return searchChildIndex(branchNum) >= 0;
  }

  /**
   * 현재 메모리에 로드된 자식 노드를 branchNum으로 조회합니다.
   *
   * @throws IllegalArgumentException 해당 branchNum이 자식 브랜치가 아닌 경우
   * @throws IllegalStateException 해당 자식 브랜치가 아직 로딩되지 않은 경우
   */
  public PersistentTreeNode<T> getChild(final int branchNum) {
    ensureChildBranchExists(branchNum);

    final int index = searchChildIndex(branchNum);
    if (index < 0) {
      throw new IllegalStateException("Child branch is not loaded: " + branchNum);
    }
    return requirePersistentNode(children.get(index));
  }

  /** 현재 노드부터 메모리에 로드된 persistent subtree를 DFS pre-order로 순회합니다. */
  public Iterator<PersistentTreeNode<T>> persistentTraversal() {
    final Iterator<TreeNode<T>> traversal = traversal();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return traversal.hasNext();
      }

      @Override
      public PersistentTreeNode<T> next() {
        return requirePersistentNode(traversal.next());
      }
    };
  }

  /**
   * DB에 저장하면서 발급된 Point ID를 노드에 반영합니다.
   *
   * @param id 저장된 Point ID
   */
  public void markPersisted(final Long id) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    this.id = id;
  }

  /**
   * 현재 노드 아래에 아직 저장되지 않은 자식 노드를 추가합니다.
   * 반환된 노드는 다음 {@code TreeService.save(tree)} 호출 때 Point로 저장됩니다.
   *
   * @param item 새 자식 노드가 가리키는 item
   * @return 새로 추가된 자식 노드
   */
  @Override
  public PersistentTreeNode<T> appendChild(final T item) {
    final PersistentTreeNode<T> child = requirePersistentNode(super.appendChild(item));

    childBranchNums = Arrays.copyOf(childBranchNums, childBranchNums.length + 1);
    childBranchNums[childBranchNums.length - 1] = child.getBranchNum();
    return child;
  }

  /**
   * 독립적으로 구성된 persistent subtree 노드를 현재 노드의 자식으로 붙입니다.
   *
   * @param treeNode parent와 tree가 없는 독립 persistent treeNode
   * @return 현재 노드 아래로 붙은 subtree root
   */
  @Override
  public PersistentTreeNode<T> appendChild(final TreeNode<T> treeNode) {
    final PersistentTreeNode<T> child = requirePersistentNode(super.appendChild(treeNode));

    childBranchNums = Arrays.copyOf(childBranchNums, childBranchNums.length + 1);
    childBranchNums[childBranchNums.length - 1] = child.getBranchNum();
    return child;
  }

  /**
   * 이미 childBranchNums에 등록된 자식 노드를 현재 노드의 로딩된 자식 목록에 연결합니다.
   *
   * @param child 현재 노드를 부모로 가진 자식 노드
   */
  public void loadChild(final PersistentTreeNode<T> child) {
    if (child.getParent() != this) {
      throw new IllegalArgumentException("child must belong to this parent");
    }
    ensureChildBranchExists(child.getBranchNum());

    final int index = searchChildIndex(child.getBranchNum());
    if (index >= 0) {
      children.set(index, child);
    } else {
      children.add(-index - 1, child);
    }
  }

  /**
   * 이 노드와 이미 로딩된 하위 노드의 소속 Tree를 설정합니다.
   * Tree 생성자나 로딩 과정에서만 사용되는 패키지 내부 메서드입니다.
   *
   * @param tree 연결할 Tree
   */
  void attachToTree(final Tree<T> tree) {
    this.tree = Objects.requireNonNull(tree, "tree must not be null");
    for (final TreeNode<T> child : children) {
      requirePersistentNode(child).attachToTree(tree);
    }
  }

  @Override
  protected PersistentTreeNode<T> createChild(final T item, final int branchNum) {
    return new PersistentTreeNode<>(null, item, this, branchNum, null);
  }

  @Override
  protected boolean hasChildBranches() {
    return childBranchNums.length > 0;
  }

  @Override
  protected void validateAppendSubtree(final TreeNode<T> treeNode) {
    super.validateAppendSubtree(treeNode);
    requirePersistentNode(treeNode);
  }

  @Override
  protected void validateAsSubtreeRoot() {
    super.validateAsSubtreeRoot();
    if (getTree() != null) {
      throw new IllegalArgumentException("subtree root must not belong to a tree");
    }
    if (isPersisted()) {
      throw new IllegalArgumentException("persisted subtree root cannot be appended");
    }
  }

  @Override
  protected int rebaseSelf(
      final int oldRootBranchNum,
      final int newRootBranchNum,
      final int targetNextBranchNum,
      final boolean inheritsParentBranch,
      final int depthDelta) {
    tree = parent instanceof PersistentTreeNode<?> persistentParent
        ? requirePersistentNode(persistentParent).getTree()
        : null;
    int maxRebasedBranchNum = super.rebaseSelf(
        oldRootBranchNum,
        newRootBranchNum,
        targetNextBranchNum,
        inheritsParentBranch,
        depthDelta);

    for (int i = 0; i < childBranchNums.length; i += 1) {
      childBranchNums[i] = rebaseBranchNum(
          childBranchNums[i],
          oldRootBranchNum,
          newRootBranchNum,
          targetNextBranchNum,
          inheritsParentBranch);
      maxRebasedBranchNum = Math.max(maxRebasedBranchNum, childBranchNums[i]);
    }
    Arrays.sort(childBranchNums);
    return maxRebasedBranchNum;
  }

  @Override
  protected int peekNextBranchNum() {
    if (tree != null) {
      return tree.getNextBranchNum();
    }
    return super.peekNextBranchNum();
  }

  @Override
  protected int issueNextBranchNum() {
    if (tree != null) {
      return tree.issueNextBranchNum();
    }
    return super.issueNextBranchNum();
  }

  @Override
  protected void syncNextBranchNum(final int nextBranchNum) {
    if (tree != null) {
      tree.syncNextBranchNum(nextBranchNum);
    } else {
      super.syncNextBranchNum(nextBranchNum);
    }
  }

  /**
   * branchNum이 현재 노드의 child branch로 등록되어 있는지 확인합니다.
   *
   * @param branchNum 확인할 child branchNum
   * @throws IllegalArgumentException childBranchNums에 해당 branch가 없을 경우
   */
  private void ensureChildBranchExists(final int branchNum) {
    if (!hasChild(branchNum)) {
      throw new IllegalArgumentException("Child branch not found: " + branchNum);
    }
  }

  @SuppressWarnings("unchecked")
  private PersistentTreeNode<T> requirePersistentNode(final TreeNode<?> node) {
    if (!(node instanceof PersistentTreeNode<?> persistentTreeNode)) {
      throw new IllegalArgumentException("node must be a PersistentTreeNode");
    }
    return (PersistentTreeNode<T>) persistentTreeNode;
  }
}
