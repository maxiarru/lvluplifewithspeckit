package lvluplife

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import lvluplife.application.*
import lvluplife.data.*

class LvlUpApplication : Application() {
    val graph by lazy { AndroidAppGraph(this) }
}

/** Application-scoped persistence and session; no Activity or credential is stored in Room. */
class AndroidAppGraph(context: Context) {
    private var coordinator: OperationCoordinator? = null
    val session = SessionController(object : SessionOperations {
        override fun recover() = coordinator?.recover() ?: emptyList()
        override fun resumeAuthenticated(uid: String) { coordinator?.resumeAuthenticated(uid) }
        override fun clearSessionMemory() { coordinator?.clearSessionMemory() }
        override fun requiresAuthentication(uid: String) = coordinator?.requiresAuthentication(uid) ?: false
    })
    private val store = PendingDatabase.open(context.applicationContext)
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    val accounts: AccessAccounts
    init {
        val api = runCatching { ApiClient.create(BuildConfig.API_BASE_URL, session) }.getOrNull()
        accounts = if (api == null) AccessAccounts { throw OperationBlocked(ApiFailureCode.TEMPORARILY_UNAVAILABLE) }
        else {
            val operations = OperationCoordinator(store, api, session, {
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } == true
            })
            coordinator = operations
            ApiAccessAccounts(api, operations, store, session)
        }
    }
}
