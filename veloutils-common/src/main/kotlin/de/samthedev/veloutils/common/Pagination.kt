// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.common

public data class PageRequest(val page: Int, val pageSize: Int) {
    init {
        require(page >= 1) { "Page must be at least 1" }
        require(pageSize in 1..100) { "Page size must be between 1 and 100" }
    }

    public val offset: Long = (page - 1).toLong() * pageSize
}

public data class Page<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
) {
    init {
        require(page >= 1 && pageSize > 0 && totalItems >= 0)
        require(items.size <= pageSize)
    }

    public val totalPages: Int = maxOf(1, ((totalItems + pageSize - 1) / pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    public val hasPrevious: Boolean = page > 1
    public val hasNext: Boolean = page < totalPages
}
