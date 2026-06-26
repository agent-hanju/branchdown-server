package dev.hanju.branchdown.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.annotation.Nonnull;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import dev.hanju.branchdown.config.IntArrayConverter;
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.entity.id.PointId;
import dev.hanju.branchdown.util.PathUtils;

/** 하나의 포인트를 지정하는 엔티티 */
@Builder
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = { "stream", "branch" })
@EqualsAndHashCode(exclude = { "stream", "branch" })
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "points")
@DynamicUpdate
public class PointEntity {

  @EmbeddedId
  @AttributeOverride(name = "streamId", column = @Column(name = "stream_id", nullable = false))
  @AttributeOverride(name = "seq", column = @Column(name = "seq", nullable = false, comment = "스트림의 각 포인트에 붙는 번호. 0부터 시작해 순차적으로 쌓인다."))
  private PointId id;

  @Column(name = "item_id", updatable = false, comment = "저장할 아이템의 ID, root는 항상 null")
  private String itemId;

  /** 0부터 시작하는 stream 내에서의 depth */
  @Column(name = "depth", nullable = false, updatable = false, comment = "0부터 시작하는 stream 내에서의 depth")
  private int depth;

  @CreatedDate
  @Column(nullable = false, name = "created_at")
  @ColumnDefault(value = "now()")
  private Instant createdAt;

  /** 포인트가 속한 스트림 */
  @MapsId("streamId")
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "stream_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_point_to_stream"), comment="포인트가 속한 스트림의 ID")
  private StreamEntity stream;

  /** 이 포인트의 소속 브랜치, read-only */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumns(value = {
      @JoinColumn(name = "stream_id", referencedColumnName = "stream_id", nullable = false, insertable = false, updatable = false),
      @JoinColumn(name = "branch_num", referencedColumnName = "branch_num", nullable = false, insertable = false, updatable = false),
  }, foreignKey = @ForeignKey(name = "FK_point_to_branch"))
  private BranchEntity branch;

  /** 소속 브랜치 번호 — branch_num 컬럼 INSERT 소유자 */
  @Column(name = "branch_num", nullable = false, updatable = false, comment = "소속 브랜치의 branchNum")
  private Integer branchNum;

  /** 이 포인트 아래로 이어지는 브랜치의 branchNum */
  @Builder.Default
  @Column(name = "child_branch_nums", nullable = false, comment = "이 포인트를 베이스로 하는 branch_num 목록(쉼표로 구분)")
  @Convert(converter = IntArrayConverter.class)
  private int[] childBranchNums = new int[0];

  /** seq에 대한 편의 접근 메서드 */
  public int getSeq() {
    return this.id.getSeq();
  }

  /**
   * 이 포인트 아래로 이어지는 branchNum 추가
   *
   * @param branchNum 추가할 branchNum
   */
  public void addChildBranchNum(final int branchNum) {
    this.childBranchNums = PathUtils.append(this.childBranchNums, branchNum);
  }

  /**
   * DTO로 변환
   * @return 알맞은 DTO
   */
  public PointDto.Response toResponse() {
    return new PointDto.Response(this.getSeq(), this.getBranchNum(), this.depth, this.itemId, this.childBranchNums,
        this.createdAt);
  }
}
