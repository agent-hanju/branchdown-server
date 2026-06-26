package dev.hanju.branchdown.dto;

import java.time.Instant;
import java.util.List;

/** Branchdown Stream DTO */
public class StreamDto {
  private StreamDto() {
  }

  public static record Response(
      Long id,
      Instant createdAt) {
  }

  public static record WithRootResponse(
      Long id,
      PointDto.Response root,
      Instant createdAt,
      int nextSeq,
      int nextBranchNum) {
  }

  /** 서비스 계층 내부용 — 외부 API에 노출하지 않음 */
  public static record Internal(
      List<PointDto.Response> points,
      int nextSeq,
      int nextBranchNum) {
  }
}
