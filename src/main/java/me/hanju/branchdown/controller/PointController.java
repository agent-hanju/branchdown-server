package me.hanju.branchdown.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.hanju.branchdown.dto.PointDto;
import me.hanju.branchdown.service.PointService;

@Tag(name = "Point", description = "포인트 관리 API")
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@ConditionalOnProperty(
  name = "branchdown.api.enabled",
  havingValue = "true",
  matchIfMissing = false
)
public class PointController {

  private final PointService pointService;

  @Operation(
    summary = "포인트 추가",
    description = "지정한 포인트 아래에 새로운 포인트를 추가합니다 (브랜칭 포함)"
  )
  @PostMapping("/{id}/down")
  public ResponseEntity<PointDto.Response> pointDown(
    @PathVariable Long id,
    @RequestBody PointDto.DownRequest request
  ) {
    PointDto.Response response = pointService.pointDown(id, request.itemId());
    return ResponseEntity.ok(response);
  }

  @Operation(
    summary = "조상 포인트 조회",
    description = "지정한 포인트의 상위 depth에 있는 조상 포인트들을 조회합니다"
  )
  @GetMapping("/{id}/ancestors")
  public ResponseEntity<List<PointDto.Response>> getAncestors(
    @PathVariable Long id
  ) {
    List<PointDto.Response> response = pointService.getAncestors(id);
    return ResponseEntity.ok(response);
  }
}
