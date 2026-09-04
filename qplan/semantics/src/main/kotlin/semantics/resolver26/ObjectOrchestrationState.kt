package semantics.resolver26

import java.util.concurrent.ConcurrentHashMap
import model.ObjectEngineResult

/** Request-local ownership of mutable OERs and their incremental orchestration tasks. */
internal class ObjectOrchestrationState {
    private val tasks = ConcurrentHashMap<ObjectEngineResult, ObjectOrchestrationTask>()

    fun register(task: ObjectOrchestrationTask) {
        check(tasks.putIfAbsent(task.occurrence.target, task) == null) {
            "Resolver26 registered an object occurrence twice"
        }
    }

    fun task(target: ObjectEngineResult): ObjectOrchestrationTask =
        checkNotNull(tasks[target]) { "Resolver26 has no orchestration task for ancestor OER" }

    fun freezeAll() {
        tasks.values.forEach(ObjectOrchestrationTask::freezeAtQuiescence)
    }
}
