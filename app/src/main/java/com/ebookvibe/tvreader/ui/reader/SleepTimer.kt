package com.ebookvibe.tvreader.ui.reader

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Countdown for the reader's sleep timer. Uses the monotonic clock so it is unaffected by the
 * projector syncing its wall clock, and reports [SleepState] roughly every second.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onUpdate: (SleepState) -> Unit,
    private val onExpired: () -> Unit,
) {
    private var job: Job? = null
    private var deadline = 0L
    private var promptDismissed = false

    val isRunning: Boolean get() = job?.isActive == true

    fun start(minutes: Int) {
        deadline = SystemClock.elapsedRealtime() + minutes * MINUTE
        promptDismissed = false
        run()
    }

    /** "+5 min" from the still-reading prompt. */
    fun extend(minutes: Int) {
        if (!isRunning) return start(minutes)
        deadline += minutes * MINUTE
        promptDismissed = false
        emit()
    }

    /** BACK on the prompt: hide it but keep counting down. */
    fun dismissPrompt() {
        promptDismissed = true
        emit()
    }

    fun cancel() {
        job?.cancel()
        job = null
        onUpdate(SleepState())
    }

    private fun run() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    onUpdate(SleepState(remainingMs = 0, fadingOut = true))
                    onExpired()
                    return@launch
                }
                emit()
                // Wake just after the next whole second so the countdown ticks evenly.
                delay(remaining % 1000 + 5)
            }
        }
    }

    private fun emit() {
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        onUpdate(
            SleepState(
                remainingMs = remaining,
                showStillReading = remaining <= PROMPT_BEFORE_MS && !promptDismissed,
            ),
        )
    }

    companion object {
        private const val MINUTE = 60_000L
        const val PROMPT_BEFORE_MS = 60_000L

        fun format(ms: Long): String {
            val total = (ms + 999) / 1000
            return "%d:%02d".format(total / 60, total % 60)
        }
    }
}
