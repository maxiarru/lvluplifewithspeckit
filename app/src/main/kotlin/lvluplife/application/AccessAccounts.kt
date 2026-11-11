package lvluplife.application

import lvluplife.data.ApiFailureCode

/** A successful lookup explicitly distinguishes a missing profile from a failed query. */
sealed interface ProfileLookup {
    data object Required : ProfileLookup
    data class Existing(val uid: String, val username: String) : ProfileLookup
}

fun interface AccessAccounts {
    suspend fun load(uid: String): ProfileLookup
    suspend fun complete(uid: String, intentId: String, username: String): OperationSnapshot = throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE)
    suspend fun retry(ref: OperationRef): OperationSnapshot = throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE)
    suspend fun draft(uid: String): String = ""
    suspend fun saveName(uid: String, username: String) = Unit
    suspend fun pendingProfile(uid: String): OperationSnapshot? = null
}
