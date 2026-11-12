package lvluplife

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Network
import androidx.compose.runtime.mutableStateOf
import lvluplife.application.*
import lvluplife.data.*

class LvlUpApplication : Application() {
    val graph by lazy { AndroidAppGraph(this) }
}

/** Application-scoped persistence and session; no Activity or credential is stored in Room. */
class AndroidAppGraph(context: Context) {
    private var coordinator: OperationCoordinator? = null
    private var recordAdapter: RecordRepository? = null
    private var activityAdapter: ActivityRepository? = null
    val session: SessionController = SessionController(object : SessionOperations {
        override fun recover(): List<OperationSnapshot> {
            val uid = session.current()?.uid ?: return emptyList()
            val ids = recordAdapter?.recoveryIds(uid) ?: emptySet()
            val activityIds = activityAdapter?.recoveryIds(uid) ?: emptySet()
            val results = coordinator?.recover() ?: emptyList()
            recordAdapter?.rememberRecovered(results.filter { it.ref.operationId in ids })
            activityAdapter?.rememberRecovered(results.filter { it.ref.operationId in activityIds })
            return results
        }
        override fun resumeAuthenticated(uid: String) { coordinator?.resumeAuthenticated(uid) }
        override fun clearSessionMemory() { coordinator?.clearSessionMemory(); recordAdapter?.clearMemory(); activityAdapter?.clearMemory() }
        override fun requiresAuthentication(uid: String) = coordinator?.requiresAuthentication(uid) ?: false
    })
    private val store = PendingDatabase.open(context.applicationContext)
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private fun connectedNow() = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } == true
    private val connection = mutableStateOf(connectedNow())
    @Volatile private var defaultNetwork: Network? = connectivity.activeNetwork
    val accounts: AccessAccounts
    val records: RecordFlowRecords?
    val progress: RecordFlowProgress?
    val activities: CustomActivityFlowActivities?
    val history: HistoryFlowHistory?
    init {
        // Application-scoped callback: updates Compose without polling or retaining an Activity/account.
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { defaultNetwork = network }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network == defaultNetwork) connection.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            override fun onLost(network: Network) { if (network == defaultNetwork) { defaultNetwork = null; connection.value = false } }
        })
        val api = runCatching { ApiClient.create(BuildConfig.API_BASE_URL, session) }.getOrNull()
        val online = { connection.value }
        if (api == null) {
            accounts = AccessAccounts { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }
            records = null
            progress = null
            activities = null
            history = null
        } else {
            val operations = OperationCoordinator(store, api, session, online)
            coordinator = operations
            accounts = ApiAccessAccounts(api, operations, store, session)
            activities = ActivityRepository(api, operations, store, session, online).also { activityAdapter = it }
            records = RecordRepository(api, operations, store, session, online, activities).also { recordAdapter = it }
            progress = ProgressRepository(api, session)
            history = HistoryRepository(api, session, online)
        }
    }
}
