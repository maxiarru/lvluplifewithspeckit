package lvluplife.application

/** A successful lookup explicitly distinguishes a missing profile from a failed query. */
sealed interface ProfileLookup {
    data object Required : ProfileLookup
    data class Existing(val uid: String, val username: String) : ProfileLookup
}

fun interface AccessAccounts {
    suspend fun load(uid: String): ProfileLookup
}
