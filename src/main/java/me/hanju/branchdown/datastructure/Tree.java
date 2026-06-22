package me.hanju.branchdown.datastructure;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import me.hanju.branchdown.constant.StreamConstants;

/** In-memory partial loadable tree datastructure */
public class Tree<T> {

  private Long id;
  private final PersistentTreeNode<T> root;
  private final AtomicInteger nextBranchNum;
  private final Instant createdAt = Instant.now();

  public Tree() {
    this(null, createRootNode(), StreamConstants.INITIAL_BRANCH_NUM + 1);
  }

  public Tree(final Long id, final PersistentTreeNode<T> root, final int nextBranchNum) {
    this.id = id;
    this.root = Objects.requireNonNull(root, "root must not be null");
    if (root.getParent() != null) {
      throw new IllegalArgumentException("Tree root cannot have a parent");
    }
    if (root.getItem() != null) {
      throw new IllegalArgumentException("Tree root must not carry an item; root is a structural anchor");
    }
    if (root.getBranchNum() != StreamConstants.INITIAL_BRANCH_NUM) {
      throw new IllegalArgumentException("Tree root must use the initial branch number");
    }
    if (nextBranchNum <= StreamConstants.INITIAL_BRANCH_NUM) {
      throw new IllegalArgumentException("nextBranchNum must be greater than the initial branch number");
    }
    this.nextBranchNum = new AtomicInteger(nextBranchNum);
    root.attachToTree(this);
  }

  public Long getId() {
    return id;
  }

  public PersistentTreeNode<T> getRoot() {
    return root;
  }

  /** 다음에 새 branch가 필요할 때 발급될 branchNum을 조회합니다. 값을 증가시키지는 않습니다. */
  public int getNextBranchNum() {
    return nextBranchNum.get();
  }

  /** 현재 nextBranchNum을 발급하고, 다음 발급을 위해 값을 원자적으로 1 증가시킵니다. */
  int issueNextBranchNum() {
    return nextBranchNum.getAndIncrement();
  }

  /** 현재 값보다 큰 nextBranchNum만 반영합니다. */
  void syncNextBranchNum(final int nextBranchNum) {
    this.nextBranchNum.updateAndGet(current -> Math.max(current, nextBranchNum));
  }

  public boolean isPersisted() {
    return id != null;
  }
  public void markPersisted(final Long id) {
    this.id = Objects.requireNonNull(id, "id must not be null");
  }

  private static <T> PersistentTreeNode<T> createRootNode() {
    return new PersistentTreeNode<T>(
        null,
        null,
        null,
        StreamConstants.INITIAL_BRANCH_NUM,
        null);
  }
}
