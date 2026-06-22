package me.hanju.branchdown.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import me.hanju.branchdown.datastructure.PersistentTreeNode;
import me.hanju.branchdown.datastructure.Tree;
import me.hanju.branchdown.datastructure.TreeNode;
import me.hanju.branchdown.dto.PointDto;
import me.hanju.branchdown.dto.StreamDto;
import me.hanju.branchdown.service.StreamService.NewBranchSpec;
import me.hanju.branchdown.service.StreamService.NewPointSpec;
import me.hanju.branchdown.service.StreamService.ParentPointUpdate;
import me.hanju.branchdown.util.PathUtils;

/**
 * 메모리에서 다루는 Tree/TreeNode 모델과 DB에 저장되는 Stream/Point 모델을 이어주는 어댑터입니다.
 * <p>
 * Tree는 애플리케이션 코드가 다루기 좋은 객체 그래프이고, Stream/Point는 BranchDown의 영속화 모델입니다.
 * 이 서비스는 두 모델 사이를 오가며 전체 트리 로딩, 최신 경로 로딩, 부분 브랜치 로딩, 저장을 담당합니다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TreeService {

  private final StreamService streamService;

  public Tree<String> create() {
    return new Tree<>();
  }

  /**
   * 스트림에 저장된 모든 Point를 읽어 완전한 Tree 객체 그래프로 복원합니다.
   * <p>
   * 부분 로딩이 아니라 전체 로딩이므로, 모든 자식 브랜치가 실제 Point로 연결되어 있어야 합니다.
   * </p>
   *
   * @param streamId 복원할 스트림 ID
   * @return 저장소의 모든 Point를 포함하는 Tree
   */
  @Transactional(readOnly = true)
  public Tree<String> loadAllRoute(final Long streamId) {
    final List<PointDto.Response> points = streamService.getAllStreamPoints(streamId);


    // (branchNum, depth)를 키로 만    // childBranchNums에는 자식의 branchNum만 들어 있으므로,들어 부모에서 자식 Point를 빠르게 찾습니다.
    final Map<PointKey, PointDto.Response> pointByPosition = new HashMap<>();
    for (final PointDto.Response point : points) {
      final PointKey key = new PointKey(point.branchNum(), point.depth());
      if (pointByPosition.put(key, point) != null) {
        throw new IllegalStateException("Duplicate point position: " + key);
      }
    }

    // 정상 생성된 스트림은 반드시 (branchNum=0, depth=0)인 synthetic root를 가져야 합니다.
    final PointDto.Response rootPoint = points.stream()
        .filter(point -> point.depth() == 0 && point.branchNum() == 0)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Root point not found"));

    final PersistentTreeNode<String> root = buildSubtree(rootPoint, null, pointByPosition, new HashSet<>());
    return new Tree<>(streamId, root, streamService.getNextBranchNum(streamId));
  }

  /**
   * 스트림의 "가장 최근 브랜치 경로"만 Tree로 로딩합니다.
   *
   * @param streamId 로딩할 스트림 ID
   * @return 최신 경로만 로딩된 부분 Tree
   */
  @Transactional(readOnly = true)
  public Tree<String> loadLatestRoute(final Long streamId) {
    final List<PointDto.Response> route = streamService.getStreamPoints(streamId);
    if (route.isEmpty()) {
      throw new IllegalStateException("Route not found");
    }

    PersistentTreeNode<String> root = null;
    PersistentTreeNode<String> previous = null;
    for (final PointDto.Response point : route) {
      // route는 root부터 leaf까지 depth 순서로 오므로 이전 노드가 현재 노드의 부모입니다.
      validatePointDepth(point, previous);
      final PersistentTreeNode<String> node = new PersistentTreeNode<>(
          point.id(),
          point.itemId(),
          previous,
          point.branchNum(),
          point.childBranchNums());
      if (previous != null) {
        previous.loadChild(node);
      }
      if (root == null) {
        root = node;
      }
      previous = node;
    }

    return new Tree<>(
        streamId,
        Objects.requireNonNull(root, "root must not be null"),
        streamService.getNextBranchNum(streamId));
  }

  /**
   * 이미 부분 로딩된 TreeNode 아래의 특정 자식 브랜치 경로를 추가로 로딩합니다.
   * <p>
   * parent는 저장된 Point여야 하며, childBranchNum은 parent가 가진 자식 브랜치 목록에 있어야 합니다.
   * 이미 로딩된 노드는 재사용하고, 아직 로딩되지 않은 노드만 새로 만들어 Tree에 붙입니다.
   * </p>
   *
   * @param parent         자식 브랜치를 펼칠 부모 노드
   * @param childBranchNum 로딩할 자식 브랜치 번호
   * @return parent 아래에 새로 이어진 경로 노드 목록
   */
  @Transactional(readOnly = true)
  public List<PersistentTreeNode<String>> loadChildRoute(
      final PersistentTreeNode<String> parent,
      final int childBranchNum) {
    if (!parent.isPersisted()) {
      throw new IllegalArgumentException("Cannot load a child route for an unsaved node");
    }
    if (!PathUtils.contains(parent.getChildBranchNums(), childBranchNum)) {
      throw new IllegalArgumentException("Child branch not found: " + childBranchNum);
    }
    final Tree<String> tree = parent.getTree();
    if (tree == null || !tree.isPersisted()) {
      throw new IllegalArgumentException("Cannot load a child route for a node without a persisted tree");
    }

    final List<PointDto.Response> route = streamService.getBranchMessages(
        tree.getId(),
        childBranchNum,
        parent.getDepth());
    if (route.isEmpty()) {
      return List.of();
    }

    final List<PersistentTreeNode<String>> loadedNodes = new ArrayList<>(route.size());
    PersistentTreeNode<String> previous = parent;
    for (final PointDto.Response point : route) {
      final PersistentTreeNode<String> node;
      if (previous.isChildLoaded(point.branchNum())) {
        node = previous.getChild(point.branchNum());
      } else {
        validatePointDepth(point, previous);
        node = new PersistentTreeNode<>(
            point.id(),
            point.itemId(),
            previous,
            point.branchNum(),
            point.childBranchNums());
        previous.loadChild(node);
      }
      loadedNodes.add(node);
      previous = node;
    }
    return loadedNodes;
  }

  /**
   * 메모리 Tree에 새로 appendChild된 노드들을 저장소에 반영합니다.
   * <p>
   * 새 Tree라면 Stream과 synthetic root를 먼저 만들고, 이후 root 아래의 미저장 노드들을 appendChild 순서대로 Point로 저장합니다.
   * 이미 저장된 Tree라면 root도 반드시 저장된 상태여야 합니다.
   * </p>
   *
   * @param tree 저장할 Tree
   * @return 저장소 ID가 반영된 같은 Tree 인스턴스
   */
  @Transactional
  public Tree<String> save(final Tree<String> tree) {
    Objects.requireNonNull(tree, "tree must not be null");

    if (tree.isPersisted()) {
      if (!tree.getRoot().isPersisted()) {
        throw new IllegalArgumentException("Persisted tree must have a persisted root");
      }
    } else {
      if (tree.getRoot().isPersisted()) {
        throw new IllegalArgumentException("New tree cannot have a persisted root");
      }
      // StreamService가 Stream, 기본 Branch, synthetic root Point를 함께 생성합니다.
      final StreamDto.WithRootResponse created = streamService.createStream();
      tree.markPersisted(created.id());
      tree.getRoot().markPersisted(created.root().id());
    }

    savePendingNodes(tree);
    return tree;
  }

  private void savePendingNodes(final Tree<String> tree) {
    final List<PersistentTreeNode<String>> pending = new ArrayList<>();
    final Iterator<PersistentTreeNode<String>> traversal = tree.getRoot().persistentTraversal();
    while (traversal.hasNext()) {
      final PersistentTreeNode<String> node = traversal.next();
      if (!node.isPersisted()) {
        pending.add(node);
      }
    }
    pending.sort(Comparator.comparing(PersistentTreeNode::getCreatedAt));
    if (pending.isEmpty()) {
      return;
    }

    // TreeNode 인스턴스를 키로 써서 nodeKey(Long 식별자)를 부여
    final Map<PersistentTreeNode<String>, Long> nodeKeys = new IdentityHashMap<>();
    long keySeq = 0;
    for (final PersistentTreeNode<String> node : pending) {
      nodeKeys.put(node, keySeq++);
    }

    final List<NewBranchSpec> newBranches = new ArrayList<>();
    final List<NewPointSpec> newPoints = new ArrayList<>();
    final Set<Long> updatedParentIds = new HashSet<>();
    final List<ParentPointUpdate> parentUpdates = new ArrayList<>();

    for (final PersistentTreeNode<String> node : pending) {
      final PersistentTreeNode<String> parent = node.getPersistentParent();
      if (parent == null) {
        throw new IllegalStateException("Cannot save an unsaved root through pending appendChild processing");
      }

      // 부모와 branchNum이 다르면 새 branch가 필요
      if (node.getBranchNum() != parent.getBranchNum()) {
        final String path = PathUtils.joinWithComma(node.branchNumPath());
        newBranches.add(new NewBranchSpec(node.getBranchNum(), path));
      }

      newPoints.add(new NewPointSpec(
          nodeKeys.get(node),
          node.getBranchNum(),
          node.getDepth(),
          node.getItem(),
          node.getChildBranchNums()));

      // 영속된 부모의 childBranchNums는 TreeNode에 이미 반영되어 있으므로 DB에도 동기화
      if (parent.isPersisted() && updatedParentIds.add(parent.getId())) {
        parentUpdates.add(new ParentPointUpdate(
            parent.getId(),
            parent.getChildBranchNums()));
      }
    }

    final Map<Long, Long> keyToPointId = streamService.persistAppendedNodes(
        tree.getId(), newBranches, newPoints, parentUpdates, tree.getNextBranchNum());

    for (final PersistentTreeNode<String> node : pending) {
      node.markPersisted(keyToPointId.get(nodeKeys.get(node)));
    }
  }

  private PersistentTreeNode<String> buildSubtree(
      final PointDto.Response point,
      final PersistentTreeNode<String> parent,
      final Map<PointKey, PointDto.Response> pointByPosition,
      final Set<Long> attachedPointIds) {
    // 같은 Point가 두 부모 아래에 붙으면 Tree가 아니라 Graph가 되므로 로딩을 중단합니다.
    if (!attachedPointIds.add(point.id())) {
      throw new IllegalStateException("Duplicate child reference detected at point " + point.id());
    }

    validatePointDepth(point, parent);
    final PersistentTreeNode<String> node = new PersistentTreeNode<>(
        point.id(),
        point.itemId(),
        parent,
        point.branchNum(),
        point.childBranchNums());
    if (parent != null) {
      parent.loadChild(node);
    }

    // Point 응답은 자식 Point 자체가 아니라 자식 branchNum 목록만 들고 있습니다.
    // 따라서 현재 depth + 1에서 같은 branchNum을 가진 Point를 찾아 실제 자식 노드로 연결합니다.
    for (final int childBranchNum : point.childBranchNums()) {
      final PointDto.Response child = pointByPosition.get(new PointKey(childBranchNum, point.depth() + 1));
      if (child == null) {
        throw new IllegalStateException("Child point not found for branch " + childBranchNum);
      }
      buildSubtree(child, node, pointByPosition, attachedPointIds);
    }

    return node;
  }

  private record PointKey(int branchNum, int depth) {
  }

  private void validatePointDepth(final PointDto.Response point, final TreeNode<String> parent) {
    final int expectedDepth = parent == null ? 0 : parent.getDepth() + 1;
    if (point.depth() != expectedDepth) {
      throw new IllegalStateException("Invalid point depth: " + point.depth());
    }
  }
}
