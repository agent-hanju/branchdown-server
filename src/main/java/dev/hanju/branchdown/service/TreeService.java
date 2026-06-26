package dev.hanju.branchdown.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import dev.hanju.branchdown.BranchdownLoadResult;
import dev.hanju.branchdown.BranchdownTree;
import dev.hanju.branchdown.BranchdownTreeNode;
import dev.hanju.branchdown.dto.StreamDto;
import dev.hanju.branchdown.interfaces.BranchdownTreeLoader;

/** DB의 Stream/Point 모델을 BranchdownTree 라이브러리가 요구하는 route로 변환하는 어댑터입니다. */
@Service
@RequiredArgsConstructor
public class TreeService implements BranchdownTreeLoader<Long, String> {

  private final StreamService streamService;

  @Override
  @Nonnull
  @Transactional(readOnly = true)
  public BranchdownLoadResult<String> loadRoute(@Nonnull final Long streamId, final int branchNum) {
    return toLoadResult(streamService.getBranchPoints(streamId, branchNum, -1));
  }

  @Override
  @Nonnull
  @Transactional(readOnly = true)
  public BranchdownLoadResult<String> loadLatestRoute(@Nonnull final Long streamId) {
    return toLoadResult(streamService.getLatestBranchPoints(streamId));
  }

  @Transactional
  public void saveTree(@Nonnull final Long streamId, @Nonnull final BranchdownTree<String> tree) {
    streamService.saveTree(streamId, tree);
  }

  private BranchdownLoadResult<String> toLoadResult(final StreamDto.Internal internal) {
    final List<BranchdownTreeNode<String>> nodes = internal.points().stream()
        .map(p -> new BranchdownTreeNode<String>(p.seq(), p.itemId(), p.branchNum(), p.childBranchNums()))
        .toList();
    return new BranchdownLoadResult<>(nodes, internal.nextSeq(), internal.nextBranchNum());
  }
}
