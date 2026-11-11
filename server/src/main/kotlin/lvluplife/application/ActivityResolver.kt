package lvluplife.application

import lvluplife.model.*
import lvluplife.persistence.CustomActivityStore

/** Definition and attributes come exclusively from the canonical catalogue or the verified owner's storage. */
class ActivityResolver(private val custom: CustomActivityStore) {
    fun resolve(uid: Uid, id: ActivityId): ActivityDefinition? =
        ActivityCatalog.find(id) ?: custom.find(uid, id)?.definition
}
