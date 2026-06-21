# Database Design

## 테이블 구조

### streams

| 컬럼              | 타입         | 제약               | 설명                      |
| ----------------- | ------------ | ------------------ | ------------------------- |
| `stream_id`       | BIGINT       | PK, IDENTITY       | 스트림 ID                 |
| `next_branch_num` | INT          | DEFAULT 0          | 다음에 생성될 브랜치 번호 |
| `created_at`      | TIMESTAMP(6) | NOT NULL           | 생성 시간                 |

### branches

| 컬럼         | 타입         | 제약                        | 설명                               |
| ------------ | ------------ | --------------------------- | ---------------------------------- |
| `stream_id`  | BIGINT       | PK, FK → streams(stream_id) | 소속 스트림 ID                     |
| `branch_num` | INT          | PK                          | 스트림 내 브랜치 번호 (0부터 시작) |
| `path`       | VARCHAR(500) | NOT NULL, DEFAULT ''        | 브랜치 경로 (예: "0,1,5")          |

**Primary Key:** (`stream_id`, `branch_num`) - Composite Key

### points

| 컬럼                | 타입         | 제약                                | 설명                                           |
| ------------------- | ------------ | ----------------------------------- | ---------------------------------------------- |
| `point_id`          | BIGINT       | PK, IDENTITY                        | 포인트 고유 ID                                 |
| `stream_id`         | BIGINT       | FK → branches(stream_id, branch_num), NOT NULL | 소속 스트림 ID                    |
| `branch_num`        | INT          | FK → branches(stream_id, branch_num), NOT NULL | 소속 브랜치 번호                  |
| `item_id`           | VARCHAR(255) | NULLABLE                            | 저장할 아이템의 ID (root는 NULL)               |
| `depth`             | INT          | NOT NULL                            | 깊이 (0부터 시작)                              |
| `child_branch_nums` | VARCHAR(256) | DEFAULT ''                          | 이 포인트에서 분기된 브랜치 번호들 (쉼표 구분) |
| `created_at`        | TIMESTAMP(6) | NOT NULL                            | 생성 시간                                      |

## Branch 조회 방식 (Path-based Query)

Append-only 구조를 활용하여 특정 Branch의 모든 Point를 효율적으로 조회합니다.

**예시:** Branch 1의 path가 `"0"` → Branch 0에서 분기

1. **path의 branchNum들의 Point 조회**

   - `branch_num IN (0, 1)` 조건으로 모든 Point 가져오기

2. **depth별로 가장 큰 branchNum만 선택**

   - 같은 depth에 여러 branch의 Point가 있으면 가장 큰 branchNum만 유지

3. **branchNum이 줄어드는 구간 자르기 (애플리케이션 레벨)**

   - Append-Only 특성 상 branchNum은 depth에 따라 증가.
   - 조회된 point 중 branchNum 이 감소하기 시작하는 구간은 path에 속하는 다른 branch의 point들이므로 버림

**장점:** 재귀 쿼리 없이 단일 쿼리 + 애플리케이션 필터링

---

상세한 구현은 Entity 코드 참조:

- [StreamEntity](../src/main/java/me/hanju/branchdown/entity/StreamEntity.java)
- [BranchEntity](../src/main/java/me/hanju/branchdown/entity/BranchEntity.java)
- [PointEntity](../src/main/java/me/hanju/branchdown/entity/PointEntity.java)
