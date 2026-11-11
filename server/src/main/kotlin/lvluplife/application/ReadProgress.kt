package lvluplife.application

import lvluplife.model.Progression
import lvluplife.model.Uid
import lvluplife.persistence.RecordStore
import lvluplife.persistence.RevisionTransaction
import lvluplife.progression.ReplayEngine

/** Never treats a stored projection or old receipt as the current source of progress. */
class ReadProgress(private val store: RecordStore, private val transactions: RevisionTransaction) {
    fun execute(uid: Uid): Progression = transactions.publishProgress(uid, { snapshot, now, revision ->
        val cycle = store.cycle(uid, snapshot)
        val records = snapshot.records.map { (id, fields) -> store.record(id, fields) }
        val replay = ReplayEngine.rebuild(cycle, records, now)
        Progression.derived(cycle, revision, now, replay.attributes, replay.general, replay.appearance.classId, replay.appearance.stage)
    }, store::projectionFields)
}
