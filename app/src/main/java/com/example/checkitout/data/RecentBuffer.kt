package com.example.checkitout.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Thread-safe ring buffer of recently observed tracks.
 *
 * Why this exists: the user may press the "like" button slightly after the next song
 * has already started. With a buffer we can recover the track that was playing a
 * few seconds before the press.
 */
class RecentBuffer(private val capacity: Int = 10) {

    private val lock = Any()
    private val deque = ArrayDeque<TrackInfo>(capacity)

    private val _state = MutableStateFlow<List<TrackInfo>>(emptyList())
    val state: StateFlow<List<TrackInfo>> = _state.asStateFlow()

    /** Records a track if it differs from the most recent one. */
    fun push(track: TrackInfo) {
        synchronized(lock) {
            val latest = deque.peekFirst()
            if (latest != null && latest.identity == track.identity) {
                return // same song still playing, ignore
            }
            deque.addFirst(track)
            while (deque.size > capacity) deque.removeLast()
            _state.value = deque.toList()
        }
    }

    /** Returns the current track (most recent observation). */
    fun current(): TrackInfo? = synchronized(lock) { deque.peekFirst() }

    /**
     * Returns the best candidate to "like" given the time the user pressed the button.
     *
     * Strategy: if a song change happened within [graceMillis] before the press,
     * the user probably wanted the *previous* song. Otherwise the current one.
     */
    fun bestCandidate(pressedAt: Long, graceMillis: Long = 3_000L): TrackInfo? =
        synchronized(lock) {
            val list = deque.toList()
            if (list.isEmpty()) return null
            val newest = list[0]
            val switchedRecently = pressedAt - newest.observedAt < graceMillis
            return if (switchedRecently && list.size >= 2) list[1] else newest
        }

    /** Returns track at index (0 = current, 1 = previous, ...). Null if out of range. */
    fun at(index: Int): TrackInfo? = synchronized(lock) {
        deque.toList().getOrNull(index)
    }
}
