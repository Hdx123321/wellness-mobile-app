package com.wellnessmate.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination envelope shared by list APIs. @author TODO(team member) */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
  public static <S, T> PageResponse<T> from(Page<S> source, List<T> content) {
    return new PageResponse<>(content, source.getNumber(), source.getSize(),
        source.getTotalElements(), source.getTotalPages());
  }
}
