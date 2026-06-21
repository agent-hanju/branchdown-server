package me.hanju.branchdown.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;
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
import me.hanju.branchdown.dto.StreamDto;

/** 여러 브랜치를 관리하는 하나의 흐름 엔티티 */
@Builder
@Getter
@Setter(AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@ToString(exclude = { "branches" })
@EqualsAndHashCode(exclude = { "branches" })
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

  /** 다음에 붙일 브랜치 번호(addBranch 시 동시에 업데이트) */
  @Builder.Default
  @Column(name = "next_branch_num", comment = "다음에 붙일 브랜치 번호")
  private Integer nextBranchNum = 0;

  public void addBranch(final BranchEntity branch) {
    if (branch != null) {
      this.branches.add(branch);
      nextBranchNum = Math.max(nextBranchNum, branch.getBranchNum() + 1);
    }
  }

  /**
   * DTO로 변환
   * @return 알맞은 DTO
   */
  public StreamDto.Response toResponse() {
    return new StreamDto.Response(this.id, this.createdAt);
  }
}
