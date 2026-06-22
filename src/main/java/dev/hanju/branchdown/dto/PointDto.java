package dev.hanju.branchdown.dto;

import java.time.Instant;

/** Branchdown Point DTO */
public class PointDto {
  private PointDto() {
  }

  public static record Response(
      Long id,
      Integer branchNum,
      Integer depth,
      String itemId,
      int[] childBranchNums,
      Instant createdAt) {
  }

  public static record DownRequest(String itemId) {
  }
}
