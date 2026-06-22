package me.hanju.branchdown.entity.id;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** BranchEntity를 위한 ID 객체 */
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Embeddable
public class BranchId implements Serializable {
  private static final long serialVersionUID = 0L;

  /** 브랜치가 속한 스트림의 ID */
  private Long streamId;

  /** 스트림의 각 브랜치에 붙는 번호. 0부터 시작해 순차적으로 쌓인다. */
  private int branchNum;
}
