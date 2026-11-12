package lvluplife.application

/** Compile-only UI contract for T073. Adapter, confirmation and recovery UI belong to T076. */
interface ResetFlowOperations {
    val online: Boolean
    suspend fun pending(uid: String): List<OperationSnapshot>
    suspend fun submit(uid: String, intentId: String, cycleId: String, confirmed: Boolean): OperationSnapshot
    suspend fun retry(ref: OperationRef): OperationSnapshot
}
