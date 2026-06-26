package dev.hanju.branchdown.dto;

/** Branchdown Branch DTO */
public class BranchDto {
  private BranchDto() {
  }

  /** 서비스 계층 내부용 — saveTree 분석 단계에서 저장 단계로 넘기는 중간 표현 */
  public static record Internal(
      int branchNum,
      String path) {
  }
}
