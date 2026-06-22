package dev.hanju.branchdown.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import dev.hanju.branchdown.dto.PointDto;
import dev.hanju.branchdown.dto.StreamDto;
import dev.hanju.branchdown.service.StreamService;

@Tag(name = "Stream", description = "스트림 관리 API")
@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "branchdown.api.enabled", havingValue = "true", matchIfMissing = false)
public class StreamController {

  private final StreamService streamService;

  @Operation(summary = "스트림 생성", description = "새로운 스트림을 생성합니다")
  @PostMapping
  public ResponseEntity<StreamDto.Response> createStream() {
    StreamDto.WithRootResponse created = streamService.createStream();
    StreamDto.Response response = new StreamDto.Response(created.id(), created.createdAt());
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "스트림 조회", description = "스트림을 조회합니다")
  @GetMapping("/{id}")
  public ResponseEntity<StreamDto.Response> getStream(@PathVariable Long id) {
    StreamDto.Response response = streamService.getStream(id);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "스트림 삭제", description = "스트림을 삭제합니다")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteStream(@PathVariable Long id) {
    streamService.deleteStream(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "스트림 포인트 목록 조회", description = "스트림의 최신 브랜치까지의 포인트 목록을 조회합니다")
  @GetMapping("/{id}/points")
  public ResponseEntity<List<PointDto.Response>> getStreamPoints(
      @PathVariable Long id) {
    List<PointDto.Response> response = streamService.getStreamPoints(id);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "브랜치 포인트 목록 조회", description = "특정 브랜치의 depth 이후 포인트 목록을 조회합니다")
  @GetMapping("/{id}/branches/{branchNum}/points")
  public ResponseEntity<List<PointDto.Response>> getBranchMessages(
      @PathVariable Long id,
      @PathVariable int branchNum,
      @RequestParam(name = "depth", defaultValue = "0") int depth) {
    List<PointDto.Response> response = streamService.getBranchMessages(
        id,
        branchNum,
        depth);
    return ResponseEntity.ok(response);
  }
}
