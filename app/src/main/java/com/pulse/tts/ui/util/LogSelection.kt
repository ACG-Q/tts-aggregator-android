package com.pulse.tts.ui.util

/** 选中集合的切换：不存在则加入，已存在则移除。 */
fun toggleSelection(current: Set<Long>, id: Long): Set<Long> =
    if (id in current) current - id else current + id

/** 仅对当前可见项反选，避免把已不可见的旧选中项带回来。 */
fun invertSelection(all: Collection<Long>, current: Set<Long>): Set<Long> =
    all.toSet() - current

/** 可见项非空且全部已选中。 */
fun isAllSelected(all: Collection<Long>, current: Set<Long>): Boolean =
    all.isNotEmpty() && all.all { it in current }
