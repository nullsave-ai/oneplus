package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Channel(val id: Int, val name: String, val category: String)

interface ChannelRepository { val channels: Flow<List<Channel>> }

/** Placeholder source. Replace with the real data layer (API / cache). */
class SampleChannelRepository : ChannelRepository {
    private val categories = listOf("أخبار", "رياضة", "أفلام", "وثائقي")
    override val channels: Flow<List<Channel>> =
        flowOf(List(24) { Channel(it, "قناة ${it + 1}", categories[it % categories.size]) })
}
