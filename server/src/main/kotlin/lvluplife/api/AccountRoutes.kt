package lvluplife.api

import com.google.cloud.firestore.Firestore
import lvluplife.application.AuthoritativeClock

/** T017 compile-time contract only; account services/routes are implemented in T020–T021. */
fun accountHandlers(firestore: Firestore, clock: AuthoritativeClock): ApiHandlers = ApiHandlers()
