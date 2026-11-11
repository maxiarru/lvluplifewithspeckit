package lvluplife.application

/** Compile-only contracts for T047; repository and UI are implemented in T051. */
data class CustomActivityDraft(val name: String, val category: String, val primary: String, val secondary: String? = null)
data class CustomActivityOption(val id: String, val name: String, val category: String, val primary: String, val secondary: String? = null)
interface CustomActivityFlowActivities {
    val online: Boolean
    suspend fun list(uid: String): List<CustomActivityOption>
    suspend fun draft(uid: String): CustomActivityDraft?
    suspend fun saveDraft(uid: String, draft: CustomActivityDraft)
    suspend fun pending(uid: String): List<OperationSnapshot>
    suspend fun submit(uid: String, intentId: String, draft: CustomActivityDraft): OperationSnapshot
    suspend fun retry(ref: OperationRef): OperationSnapshot
}
