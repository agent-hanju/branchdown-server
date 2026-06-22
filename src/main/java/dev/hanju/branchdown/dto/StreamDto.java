package dev.hanju.branchdown.dto;

import java.time.Instant;

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
      Instant createdAt) {
  }
}
