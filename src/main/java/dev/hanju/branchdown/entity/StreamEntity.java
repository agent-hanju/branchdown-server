package dev.hanju.branchdown.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import dev.hanju.branchdown.constant.BranchdownConstants;
import dev.hanju.branchdown.dto.StreamDto;

/** 여러 브랜치를 관리하는 하나의 흐름 엔티티 */
@Builder
@Getter
@Setter(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@ToString(exclude = { "branches", "points" })
@EqualsAndHashCode(exclude = { "branches", "points" })
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "streams")
@DynamicUpdate
public class StreamEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "stream_id")
  private Long id;

  @CreatedDate
  @Column(nullable = false, name = "created_at")
  @ColumnDefault(value = "now()")
  private Instant createdAt;

  @Builder.Default
  @OneToMany(mappedBy = "stream", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  private List<BranchEntity> branches = new ArrayList<>();

  @Builder.Default
  @OneToMany(mappedBy = "stream", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PointEntity> points = new ArrayList<>();

  @Builder.Default
  @Column(name = "next_seq", comment = "다음에 발급할 스트림 내 seq")
  private Integer nextSeq = 0;

  /** 다음에 붙일 브랜치 번호(addBranch 시 동시에 업데이트) */
  @Builder.Default
  @Column(name = "next_branch_num", comment = "다음에 붙일 브랜치 번호")
  private Integer nextBranchNum = BranchdownConstants.INITIAL_BRANCH_NUM;

  public void addBranch(final BranchEntity branch) {
    if (branch != null) {
      this.branches.add(branch);
      nextBranchNum = Math.max(nextBranchNum, branch.getBranchNum() + 1);
    }
  }
  public void addPoint(final PointEntity point) {
    if(point != null) {
      this.points.add(point);
      nextSeq = Math.max(nextSeq, point.getSeq() + 1);
    }
  }

  public void syncBranchesAndPoints(final List<BranchEntity> branches, final List<PointEntity> points) {
    this.branches.addAll(branches);
    this.points.addAll(points);
    branches.stream().mapToInt(b -> b.getBranchNum() + 1).max().ifPresent(max -> this.nextBranchNum = Math.max(this.nextBranchNum, max));
    points.stream().mapToInt(p -> p.getSeq() + 1).max().ifPresent(max -> this.nextSeq = Math.max(this.nextSeq, max));
  }
  

  /**
   * DTO로 변환
   * @return 알맞은 DTO
   */
  public StreamDto.Response toResponse() {
    return new StreamDto.Response(this.id, this.createdAt);
  }
}
