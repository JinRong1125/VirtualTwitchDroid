package com.example.virtualtwitchdroid.core.model

/**
 * One page of a cursor-paginated list: the [items] just fetched, plus [nextCursor] to request the
 * following page. A null [nextCursor] means there are no more pages (end of the list).
 */
data class Page<T>(val items: List<T>, val nextCursor: String? = null)
