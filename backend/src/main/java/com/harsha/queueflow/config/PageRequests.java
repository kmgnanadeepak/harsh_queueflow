package com.harsha.queueflow.config;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds bounded {@link Pageable}s from query parameters so a client can never
 * request an unbounded page size.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        return of(page, size, null);
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        PageRequest request = PageRequest.of(safePage, safeSize);
        return sort == null ? request : PageRequest.of(safePage, safeSize, sort);
    }
}
