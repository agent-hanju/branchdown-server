package dev.hanju.branchdown.dto;

import java.time.Instant;

/** Branchdown Point DTO */
public class PointDto {
  private PointDto() {
  }

  public static record Response(
      int seq,
      Integer branchNum,
      Integer depth,
      String itemId,
      int[] childBranchNums,
      Instant createdAt) {
  }

  public static record DownRequest(String itemId) {
  }

  /** 서비스 계층 내부용 — saveTree 분석 단계에서 저장 단계로 넘기는 중간 표현 */
  public static record Internal(
      int seq,
      int branchNum,
      int depth,
      String itemId,
      int[] childBranchNums) {
  }
}
