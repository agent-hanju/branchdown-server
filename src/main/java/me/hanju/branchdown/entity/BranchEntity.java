package me.hanju.branchdown.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
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
import me.hanju.branchdown.entity.id.BranchId;

/** 분기 관리를 위한 중간 엔티티 */
@Builder
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "branches")
@ToString(exclude = { "stream", "points" })
@EqualsAndHashCode(exclude = { "stream", "points" })
public class BranchEntity {

  @EmbeddedId
  @AttributeOverride(name = "streamId", column = @Column(name = "stream_id", nullable = false))
  @AttributeOverride(name = "branchNum", column = @Column(name = "branch_num", nullable = false, comment = "스트림의 각 브랜치에 붙는 번호. 0부터 시작해 순차적으로 쌓인다."))
  private BranchId id;

  /** 브랜치가 속한 스트림 */
  @MapsId("streamId")
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "stream_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_branch_to_stream"), comment="브랜치가 속한 스트림의 ID")
  private StreamEntity stream;

  /** 자기 자신까지 오기 위한 branch_num의 경로. 구분자는 "," */
  @Builder.Default
  @Column(nullable = false, updatable = false, length = 500, comment = "자기 자신까지 오기 위한 branch_num의 경로. 구분자는 \",\"")
  private String path = "";

  /** 이 브랜치 소속 포인트들 */
  @Builder.Default
  @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PointEntity> points = new ArrayList<>();

  /**
   * 브랜치에 포인트를 추가
   *
   * @param point 추가할 PointEntity
   */
  public void addPoint(PointEntity point) {
    if (point != null) {
      this.points.add(point);
    }
  }

  /** branchNum에 대한 편의 접근 메서드 */
  public int getBranchNum() {
    return this.id.getBranchNum();
  }
}
