# Tree Structure Design

BranchDown은 DB에는 `Stream / Branch / Point` 구조로 저장하고, 코드 안에서는 `Tree / TreeNode` 객체 구조로 사용할 수 있도록 `TreeService` 어댑터를 둔다.

## 목적

- 애플리케이션 코드에서는 일반적인 트리 객체처럼 `TreeNode.append(...)`, `getChildren()`을 사용한다.
- 저장 시에는 BranchDown의 append-only 규칙에 맞춰 `PointService.pointDown(...)`을 재생한다.
- 조회 시에는 DB의 branch/path 정보를 Tree 객체로 복원한다.
- 전체 트리 로드뿐 아니라 root-to-leaf 경로만 부분 적재하고, 이후 사용자가 특정 branch를 선택하면 해당 route를 추가 적재할 수 있다.

## 객체 모델

### Tree

- `Tree.id`는 DB의 `streams.stream_id`에 대응한다.
- 신규 Tree는 저장 전 `id == null`이다.
- `Tree.nextBranchNum`은 다음에 새로 만들 브랜치 번호를 뜻한다.
  신규 Tree는 root branch 0을 이미 가진 것으로 보고 `nextBranchNum == 1`로 시작한다.
- `Tree.root`는 데이터를 담지 않는 구조적 앵커이며, root의 itemId는 항상 null이다. 어떤 생성 경로도 root에 itemId를 넣을 수 없다(생성자 가드로 강제).
- `Tree`는 stream 전체에서 다음에 발급할 branchNum만 관리한다.
  branchNum이 실제로 필요한지는 append 주체인 `TreeNode`가 자신의 child 상태를 보고 결정한다.

### TreeNode

- `TreeNode.id`는 DB의 `points.point_id`에 대응한다.
- `TreeNode.itemId`는 DB의 `points.item_id`에 대응한다. 단 root는 구조적 앵커이므로 root의 itemId는 항상 null이다.
- `TreeNode.tree`는 소속 `Tree` aggregate를 가리키며, `TreeNode`의 stream id는 `node.getTree().getId()`로 확인한다.
- `TreeNode.depth`는 DB의 `points.depth`에 대응한다.
- `TreeNode.branchNum`은 DB의 `points.branch_num`에 대응한다.
  root도 초기 브랜치 번호인 0을 가진다.
- `TreeNode.parent`와 `TreeNode.getChildren()`은 현재 메모리에 적재된 객체 관계를 나타낸다.
- `TreeNode.childBranchNums`는 저장소 기준으로 알려진 자식 branchNum 목록을 나타낸다.
  이 목록에는 아직 `TreeNode` 객체로 로드되지 않은 branch도 포함될 수 있다.
- 저장 전 append된 노드도 append 시점에 branchNum을 이미 가진다.
  따라서 저장 전후 `getChildBranchNums()`의 의미가 바뀌지 않는다.
- `TreeNode.append(...)`는 자신의 child 상태를 기준으로 새 노드의 branchNum을 결정한다.
  첫 자식은 현재 노드의 branchNum을 이어받고, 두 번째 자식부터는 소속 `Tree`에서 다음 branchNum을 발급받는다.

### Children and Child Branches

- `getChildren()`은 현재 메모리에 실제로 로드된 child `TreeNode`만 반환한다.
- `getChildBranchNums()`는 DB 기준으로 알려진 child branch 목록을 반환한다.
- 부분 로드된 노드는 `getChildBranchNums()`에 있는 branchNum 중 일부만 `getChildren()`으로 갖고 있을 수 있다.
  외부 사용자는 추가 로드할 branchNum을 `getChildBranchNums()`에서 선택한다.

## 부분 적재

`TreeService.loadLatestRoute(streamId)`는 해당 stream의 최신 leaf route만 Tree 객체로 만든다.

- `StreamService.getStreamPoints(streamId)`의 조회 전략을 그대로 사용한다.
- 최신 point가 속한 branch path를 기준으로 root부터 최신 leaf까지의 route를 로드한다.
- route에 포함된 각 node는 자신의 DB `child_branch_nums`를 `TreeNode.childBranchNums`로 보관한다.
- 따라서 현재 메모리에 로드된 `children`만 있어도, 각 node는 로드 시점의 DB 기준 child branch 목록을 유지한다.
- 부분 적재된 Tree도 Stream의 `nextBranchNum`을 함께 보관하므로 append/save가 가능하다.

예를 들어 DB에는 `A -> A1`, `A -> A2`가 있지만 route 적재 결과가 `A -> A1`만 포함할 수 있다.
이때 `A.getChildren()`은 `A1`만 반환하지만, `A.getChildBranchNums()`는 `A1`, `A2`에 해당하는 branch 후보를 모두 포함한다.

`pointId`만으로 조상을 역조회해 Tree route를 구성하는 별도 load API는 두지 않는다.
일반적인 사용 흐름에서는 stream을 먼저 조회해야 화면에 노출되는 point id를 알 수 있고, Tree 부분 적재도 기존 Stream 조회 전략을 재사용하는 편이 BranchDown의 API 모델과 잘 맞는다.

## 추가 적재

사용자가 “다른 child를 볼래”라고 선택하면, `TreeService.loadChildRoute(parent, childBranchNum)`으로 해당 branch의 route를 추가 적재한다.

동작 방식:

1. 선택한 `childBranchNum`이 parent의 `childBranchNums`에 있는 branch인지 확인한다.
2. `parent.getTree().getId()`, `parent.depth`, `childBranchNum`을 이용해 해당 branch route를 조회한다.
3. 조회된 route를 기존 TreeNode 아래에 이어 붙인다.
4. 이미 로드된 child는 중복 생성하지 않는다.

`branchNum`만으로 child point를 전역 식별하지는 않는다.
추가 적재에는 parent의 소속 Tree와 depth가 필요하다.
즉 미로드 child의 `point_id`는 미리 몰라도 되고, route 조회 과정에서 자연스럽게 알게 된다.

## 저장

`TreeService.save(tree)`는 bulk insert가 아니라 append 이벤트 재생 방식이다.

- 신규 Tree는 `StreamService.createStream()`로 Stream, Branch 0, synthetic root Point(itemId=null)를 만든다.
- 저장되지 않은 TreeNode들은 `append()` 호출 순서대로 정렬된다.
- `TreeService`는 `PointService.pointDown(parentId, itemId)`를 append 순서대로 호출한다.
- `PointService`는 DB의 현재 parent 상태와 stream의 `next_branch_num`을 기준으로 branchNum을 배정한다.
- `TreeService`는 저장 결과의 branchNum이 append 시점에 TreeNode가 가진 `node.branchNum`과 같은지만 검증한다.
  다르면 stale Tree 또는 동시 수정 충돌로 보고 예외를 던진다.
- branch path, parent의 `child_branch_nums`, stream의 `next_branch_num`은 DB 저장 흐름에서 결정된 branchNum을 기준으로 반영된다.

즉 Tree 저장은 Tree의 branchNum을 DB 레이어에 강제로 주입하지 않는다.
Tree와 DB는 같은 append 순서와 같은 시작 상태라면 같은 branchNum을 배정해야 하며, 로드할 때는 DB를 true source로 보고 Tree 객체를 다시 구성한다.

## Bulk 저장 검토

Bulk 저장은 가능하지만 별도 최적화 이슈로 분리한다.

서비스 레벨 bulk planner를 만들려면 append 순서대로 다음 값을 메모리에서 계산해야 한다.

- parent point의 현재 `child_branch_nums`
- 기존 branch 사용 여부
- 신규 branch 번호와 `path`
- 새 point의 depth
- stream의 `next_branch_num`

진짜 DB native bulk insert까지 가려면 PostgreSQL identity 기반 `point_id` 생성과 generated key 처리도 함께 검토해야 한다.
