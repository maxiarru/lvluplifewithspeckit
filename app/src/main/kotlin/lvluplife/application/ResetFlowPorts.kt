package lvluplife.application

/** Explicit confirmation and recovery of the original owner's reset intent. */
interface ResetFlowOperations {
    val online: Boolean
    suspend fun pending(uid: String): List<OperationSnapshot>
    suspend fun submit(uid: String, intentId: String, cycleId: String, confirmed: Boolean): OperationSnapshot
    suspend fun retry(ref: OperationRef): OperationSnapshot
}
