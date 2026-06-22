package me.hanju.branchdown.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.hanju.branchdown.config.IntArrayConverter;
import me.hanju.branchdown.dto.PointDto;
import me.hanju.branchdown.util.PathUtils;

/** 하나의 포인트를 지정하는 엔티티 */
@Builder
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = { "branch", "childBranchNums" })
@EqualsAndHashCode(exclude = { "branch", "childBranchNums" })
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "points")
@DynamicUpdate
public class PointEntity {

  // branch_num은 insertable=false라 INSERT SQL에 포함되지 않으므로, INSERT 후 메모리상 branchNum을 branch에서 수동 보정
  @PostPersist
  public void syncBranchNum() {
    if (this.branch != null) {
      this.branchNum = this.branch.getBranchNum();
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "point_id")
  private Long id;

  @Column(name = "item_id", updatable = false, comment = "저장할 아이템의 ID, root는 항상 null")
  private String itemId;

  /** 0부터 시작하는 stream 내에서의 depth */
  @Column(name = "depth", nullable = false, updatable = false, comment = "0부터 시작하는 stream 내에서의 depth")
  private int depth;

  @CreatedDate
  @Column(nullable = false, name = "created_at")
  @ColumnDefault(value = "now()")
  private Instant createdAt;

  /** 이 포인트의 소속 브랜치 */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumns(value = {
      @JoinColumn(name = "stream_id", referencedColumnName = "stream_id", nullable = false, updatable = false),
      @JoinColumn(name = "branch_num", referencedColumnName = "branch_num", nullable = false, updatable = false),
  }, foreignKey = @ForeignKey(name = "FK_point_to_branch"))
  private BranchEntity branch;

  // ========== 읽기 전용 필드 ==========

  /**
   * 이 포인트의 소속 스트림(읽기 전용), branch 필드에 의해 결정
   * <p>
   * <strong>주의:</strong> 이 필드는 Repository의 JPQL 쿼리 전용입니다.
   * Java 코드에서는 {@code point.getBranch().getStream()}을 사용하세요.
   * </p>
   */
  @Getter(AccessLevel.PRIVATE)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stream_id", referencedColumnName = "stream_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_point_to_stream"), comment = "소속 스트림 ID")
  private StreamEntity stream;

  /** 이 포인트의 소속 브랜치 번호(읽기 전용), branch 필드에 의해 결정 */
  @Column(name = "branch_num", insertable = false, updatable = false, comment = "소속 브랜치의 branchNum")
  private Integer branchNum;
  // ====================================

  /** 이 포인트 아래로 이어지는 브랜치의 branchNum */
  @Builder.Default
  @Column(name = "child_branch_nums", nullable = false, comment = "이 포인트를 베이스로 하는 branch_num 목록(쉼표로 구분)")
  @Convert(converter = IntArrayConverter.class)
  private int[] childBranchNums = new int[0];

  /**
   * 이 포인트 아래로 이어지는 branchNum 추가
   *
   * @param branchNum 추가할 branchNum
   */
  public void addChildBranchNum(final int branchNum) {
    this.childBranchNums = PathUtils.append(this.childBranchNums, branchNum);
  }

  /**
   * childBranchNums를 새 배열로 교체합니다.
   * Tree 저장 시 영속 부모 point의 자식 목록을 일괄 갱신할 때 사용합니다.
   *
   * @param childBranchNums 교체할 배열
   */
  public void setChildBranchNums(final int[] childBranchNums) {
    this.childBranchNums = childBranchNums;
  }

  /**
   * DTO로 변환
   * @return 알맞은 DTO
   */
  public PointDto.Response toResponse() {
    return new PointDto.Response(this.id, this.getBranchNum(), this.depth, this.itemId, this.childBranchNums,
        this.createdAt);
  }
}
