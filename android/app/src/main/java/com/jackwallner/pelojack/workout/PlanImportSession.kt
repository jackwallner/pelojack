package com.jackwallner.pelojack.workout

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** File-picker workflow; deliberately has no settings writer or ride controller. */
class PlanImportSession(private val library: WorkoutLibrary, private val activeProgramId: () -> String?) {
    data class State(val busy: Boolean = false, val preview: PlanImporter.Preview? = null, val message: String? = null)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    private val running = AtomicBoolean(false)

    suspend fun inspect(open: () -> InputStream) = operation {
        val preview = open().buffered().use { input ->
            input.mark(4)
            val zip = input.read() == 'P'.code && input.read() == 'K'.code
            input.reset()
            library.previewPlan(input, zip, activeProgramId())
        }
        mutable.value = State(preview = preview)
    }

    suspend fun confirm() {
        val preview = mutable.value.preview ?: return
        operation {
            library.importPlan(preview, activeProgramId())
            mutable.value = State(message = "Imported ${preview.program.name}. Your active program is unchanged.")
        }
    }

    fun cancel() { if (!running.get()) mutable.value = State() }

    private suspend fun operation(action: () -> Unit) {
        if (!running.compareAndSet(false, true)) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        try {
            withContext(Dispatchers.IO) { action() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.value = State(message = "Import failed: ${e.message ?: "Could not read plan"}")
        } finally {
            mutable.value = mutable.value.copy(busy = false)
            running.set(false)
        }
    }
}
