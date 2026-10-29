package com.roadguard.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Simple pagination envelope shared by every paged endpoint. page is 0-based. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
