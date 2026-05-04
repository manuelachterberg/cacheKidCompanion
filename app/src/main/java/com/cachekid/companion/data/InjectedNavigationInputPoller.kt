package com.cachekid.companion.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Periodically polls a set of candidate directories for the latest injected
 * navigation input file and reports the result via [onResult].
 *
 * This decouples the polling lifecycle from [MainActivity] and makes the
 * timing behavior testable.
 */
class InjectedNavigationInputPoller(
    private val importLatest: suspend (List<File>) -> InjectedNavigationInputImportResult,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val coroutineScopeFactory: () -> CoroutineScope = { CoroutineScope(Dispatchers.Main) },
) {

    private var pollingJob: Job? = null

    /**
     * Starts periodic polling. Each tick scans [candidateDirectories] and
     * invokes [onResult] with the import outcome.
     *
     * Calling [start] while already polling is a no-op.
     */
    fun start(
        candidateDirectories: List<File>,
        onResult: (InjectedNavigationInputImportResult) -> Unit,
    ) {
        if (pollingJob?.isActive == true) {
            return
        }
        pollingJob = coroutineScopeFactory().launch {
            while (isActive) {
                val result = withContext(Dispatchers.IO) {
                    importLatest(candidateDirectories)
                }
                onResult(result)
                delay(pollIntervalMillis)
            }
        }
    }

    /**
     * Cancels the active polling job if one exists.
     */
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun isPolling(): Boolean = pollingJob?.isActive == true

    companion object {
        private const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
    }
}
