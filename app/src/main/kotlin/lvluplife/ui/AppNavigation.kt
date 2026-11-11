package lvluplife.ui

import androidx.compose.runtime.Composable
import lvluplife.application.AccessAccounts
import lvluplife.application.SessionController
import lvluplife.data.GoogleSignIn

/** T018 compile-time contract only. Access/profile navigation is implemented in T023. */
@Composable
fun AppNavigation(session: SessionController, google: GoogleSignIn, accounts: AccessAccounts) {
    SessionHost(session)
}
