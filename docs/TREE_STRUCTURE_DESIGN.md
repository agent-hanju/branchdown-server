# Tree Structure Design

BranchDown은 DB에는 `Stream / Branch / Point` 구조로 저장하고, 애플리케이션 코드에서는 `Tree / TreeNode` 객체 그래프로 다룬다. `TreeService`는 두 모델 사이를 변환하는 어댑터다.

## 목적

- 애플리케이션 코드는 일반적인 트리처럼 `appendChild(...)`, `getChildren()`을 사용한다.
- 저장소 상태가 필요한 경우에는 `PersistentTreeNode`가 Point ID, 소속 Tree, 부분 로딩 정보를 가진다.
- 전체 트리 로드뿐 아니라 최신 root-to-leaf 경로만 부분 로드하고, 이후 특정 child branch route를 추가 로드할 수 있다.
- 저장 시에는 메모리에서 append된 미저장 노드를 `StreamService.persistAppendedNodes(...)`로 한 번에 반영한다.

## 객체 모델

### Tree

- `Tree.id`는 DB의 `streams.stream_id`에 대응한다.
- 신규 `Tree`는 저장 전 `id == null`이다.
- `Tree.root`는 `PersistentTreeNode<T>`이며, 데이터를 담지 않는 구조적 앵커다.
- root의 item은 항상 `null`이고, branchNum은 초기 브랜치 번호인 `0`이다.
- `Tree.nextBranchNum`은 stream 전체에서 다음에 발급할 branchNum이다.
- 저장된 Tree는 `Tree.syncNextBranchNum(...)`으로 DB의 `next_branch_num`과 동기화된다.

### TreeNode

`TreeNode<T>`는 persistence 상태를 모르는 순수 메모리 트리 노드다.

- `item`: 노드가 가리키는 값이다.
- `parent`: 메모리에 연결된 부모 노드다. root라면 `null`이다.
- `children`: 현재 메모리에 연결된 직접 자식 노드 목록이다.
- `branchNum`: 노드가 속한 branch 번호다.
- `depth`: root부터의 깊이다. root는 `0`이다.
- `localNextBranchNum`: Tree에 속하지 않은 독립 TreeNode 그래프에서 branch 번호를 발급하기 위한 로컬 allocator다.

`appendChild(item)` 규칙:

- 현재 노드에 자식 branch가 없으면 첫 child는 부모의 `branchNum`을 계승한다.
- 이미 자식 branch가 있으면 root allocator에서 새 branchNum을 발급받는다.
- 자식은 `branchNum` 오름차순 위치에 삽입된다.

`appendChild(treeNode)` 규칙:

- parent가 없는 detached subtree root만 붙일 수 있다.
- target parent에 자식 branch가 없으면 subtree root는 parent branch를 계승한다.
- target parent에 이미 자식 branch가 있으면 target root allocator의 다음 branchNum으로 subtree 전체를 rebase한다.
- subtree 내부의 상대 branch 구조는 유지한다.
- attach/rebase 과정은 `traversal()` DFS iterator를 사용한다.

`traversal()`:

- 현재 노드부터 메모리에 로드된 subtree를 DFS pre-order로 순회하는 lazy `Iterator<TreeNode<T>>`를 반환한다.
- iterator 생성 시 subtree 전체를 미리 수집하지 않고, `next()` 호출 시 현재 노드의 loaded children을 stack에 추가한다.
- DB나 저장소에서 미로드 child를 자동 조회하지 않는다.

### PersistentTreeNode

`PersistentTreeNode<T>`는 `TreeNode<T>`에 저장 및 부분 로딩 상태를 추가한 노드다.

- `id`: DB의 `points.point_id`다. 저장 전 노드는 `null`이다.
- `tree`: 소속 `Tree` aggregate다. detached subtree라면 `null`일 수 있다.
- `childBranchNums`: 저장소 기준으로 알려진 전체 child branchNum 목록이다.
- `createdAt`: 미저장 노드를 append 순서대로 저장하기 위한 메모리 생성 시각이다.

`children`과 `childBranchNums`는 의미가 다르다.

- `getChildren()`은 현재 메모리에 실제 로드된 child node만 반환한다.
- `getChildBranchNums()`는 저장소 기준으로 알려진 child branchNum 전체를 반환한다.
- 부분 로드된 노드는 `childBranchNums`에 있는 branch 중 일부만 `children`으로 갖고 있을 수 있다.

`PersistentTreeNode`는 `TreeNode`의 관계를 persistent 타입으로 좁혀 쓰기 위한 typed accessor를 제공한다.

- `getPersistentParent()`: parent를 `PersistentTreeNode<T>`로 반환한다.
- `persistentTraversal()`: `traversal()`을 감싸 `Iterator<PersistentTreeNode<T>>`로 반환한다.
- 내부에서는 `requirePersistentNode(...)`로 `PersistentTreeNode` invariant를 검증한다.

Persistent append 검증:

- persistent parent에는 `PersistentTreeNode` subtree만 붙일 수 있다.
- subtree root는 parent가 없어야 한다.
- subtree root는 이미 Tree에 속해 있으면 안 된다.
- subtree root는 이미 persisted 상태이면 안 된다.

## 부분 적재

`TreeService.loadLatestRoute(streamId)`는 stream의 최신 leaf route만 Tree 객체로 만든다.

- `StreamService.getStreamPoints(streamId)`의 조회 전략을 사용한다.
- root부터 최신 leaf까지 depth 순서대로 `PersistentTreeNode`를 생성한다.
- 각 node는 DB 응답의 `childBranchNums`를 보관한다.
- route에 포함된 child만 `children`에 연결된다.
- 부분 로드된 Tree도 DB의 `nextBranchNum`을 함께 보관하므로 append/save가 가능하다.

예를 들어 DB에는 `A -> A1`, `A -> A2`가 있지만 최신 route가 `A -> A1`이면, `A.getChildren()`은 `A1`만 반환할 수 있다. 그래도 `A.getChildBranchNums()`는 `A1`, `A2`에 해당하는 branch 후보를 모두 가진다.

## 추가 적재

사용자가 다른 child branch를 선택하면 `TreeService.loadChildRoute(parent, childBranchNum)`으로 해당 branch route를 추가 적재한다.

동작 방식:

1. `parent`가 persisted node인지 확인한다.
2. `childBranchNum`이 parent의 `childBranchNums`에 있는지 확인한다.
3. parent의 Tree ID, parent depth, child branchNum으로 branch route를 조회한다.
4. 이미 로드된 child는 재사용하고, 미로드 node만 새로 만들어 `loadChild(...)`로 연결한다.

`branchNum`만으로 child point를 전역 식별하지 않는다. 추가 적재에는 parent의 소속 Tree와 depth가 필요하다.

## 전체 적재

`TreeService.loadAllRoute(streamId)`는 stream의 모든 Point를 읽어 완전한 Tree 객체 그래프로 복원한다.

- 모든 Point를 `(branchNum, depth)` 키로 인덱싱한다.
- synthetic root는 `(branchNum=0, depth=0)` 위치에서 찾는다.
- Point 응답의 `childBranchNums`는 child Point ID가 아니라 child branchNum 목록이므로, child는 `(childBranchNum, parent.depth + 1)`로 찾는다.
- 같은 Point가 두 부모 아래에 붙으면 graph가 되므로 `attachedPointIds`로 중복 연결을 방지한다.
- 전체 적재 결과는 모든 알려진 child branch가 실제 `children`으로 로드된 Tree다.

## 저장

`TreeService.save(tree)`는 Tree에 새로 append된 미저장 노드들을 저장소에 반영한다.

신규 Tree 저장:

- `StreamService.createStream()`이 Stream, Branch 0, synthetic root Point를 만든다.
- 생성된 stream ID와 root point ID를 `Tree`와 root node에 반영한다.

기존 Tree 저장:

- persisted Tree라면 root도 persisted 상태여야 한다.
- root의 `persistentTraversal()`로 현재 메모리에 로드된 subtree를 순회한다.
- `!node.isPersisted()`인 pending node만 수집한다.
- pending node는 `createdAt` 기준으로 정렬해 append 생성 순서를 유지한다.

저장 계획:

- pending node마다 임시 node key를 부여한다.
- node의 `branchNum`이 parent의 `branchNum`과 다르면 새 Branch가 필요하므로 `NewBranchSpec`을 만든다.
- pending node 자체는 `NewPointSpec`으로 저장한다.
- parent가 이미 persisted 상태라면 parent의 최신 `childBranchNums`를 DB에도 반영하기 위해 `ParentPointUpdate`를 만든다.
- 같은 parent는 한 번만 update한다.
- 마지막으로 `StreamService.persistAppendedNodes(...)`에 branch, point, parent update, nextBranchNum을 전달한다.
- 저장 후 반환된 point ID를 각 pending node에 `markPersisted(...)`로 반영한다.

부모도 pending node라면 parent update가 필요 없다. 그 부모는 `NewPointSpec`에 자신의 `childBranchNums`를 포함해 새 Point로 저장되기 때문이다.

## 설계 경계

- `TreeNode`는 순수 트리 구조와 branch/depth rebase 알고리즘만 담당한다.
- `PersistentTreeNode`는 저장소 ID, 소속 Tree, child branch 목록, 부분 로딩 상태를 담당한다.
- `TreeService`는 DB DTO와 메모리 Tree 객체 사이의 변환 및 저장 계획 수립을 담당한다.
- `StreamService`는 실제 DB 저장 모델인 Stream/Branch/Point를 생성, 조회, 갱신한다.

## Bulk 저장

현재 저장은 `StreamService.persistAppendedNodes(...)`를 통해 append된 node들을 한 번에 반영한다.

이때 서비스 레벨에서 계산하는 값은 다음과 같다.

- 신규 branch 번호와 path
- 새 point의 branchNum, depth, item, childBranchNums
- 기존 parent point의 갱신된 childBranchNums
- stream의 nextBranchNum

진짜 DB native bulk insert 최적화는 PostgreSQL identity 기반 `point_id` 생성과 generated key 처리까지 함께 검토해야 하며, 별도 최적화 이슈로 분리한다.
