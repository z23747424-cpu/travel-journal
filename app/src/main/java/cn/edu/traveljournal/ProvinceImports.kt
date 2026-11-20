package cn.edu.traveljournal

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only session imports are candidates; the repository checks every persisted reference. */
internal class ProvinceImportSession(private val discard:(Collection<String>)->Unit) {
    private val imported=mutableSetOf<String>()
    private var closed=false
    fun record(name:String){val dispose=synchronized(this){imported+=name;closed};if(dispose)cleanup()}
    fun cleanup(retain:Set<String> = emptySet()):Job {
        val names=synchronized(this){(imported-retain).toList()}
        return cleanupScope.launch { cleanupMutex.withLock {
            if(names.isNotEmpty()&&runCatching{discard(names)}.isSuccess)synchronized(this@ProvinceImportSession){imported.removeAll(names.toSet())}
        }}
    }
    fun close():Job {synchronized(this){closed=true};return cleanup()}
    companion object {
        private val cleanupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        private val cleanupMutex=Mutex()
    }
}
