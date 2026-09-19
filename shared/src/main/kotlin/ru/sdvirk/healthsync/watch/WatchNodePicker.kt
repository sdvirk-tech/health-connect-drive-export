package ru.sdvirk.healthsync.watch

data class WearNodeRef(
    val id: String,
    val nearby: Boolean,
    val displayName: String = id,
)

object WatchNodePicker {
    fun pick(capability: List<WearNodeRef>, connected: List<WearNodeRef>): WearNodeRef? =
        capability.firstOrNull { it.nearby }
            ?: capability.firstOrNull()
            ?: connected.firstOrNull { it.nearby }
            ?: connected.firstOrNull()
}
