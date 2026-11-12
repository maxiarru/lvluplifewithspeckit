package lvluplife.ui

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import lvluplife.LvlUpApplication
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import lvluplife.ui.theme.LvlUpTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Explicit two-process matrix. Real UI, HTTP repositories, private Room, Auth/Firestore emulators.
 * Synthetic Google claims are accepted only by local Auth; no real Google chooser is claimed. */
class DeviceMatrixStages {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val graph get()=(compose.activity.application as LvlUpApplication).graph
    private val run get()=checkNotNull(InstrumentationRegistry.getArguments().getString("matrixRun")).also { UUID.fromString(it) }
    private val cell get()=checkNotNull(InstrumentationRegistry.getArguments().getString("matrixCell")).also { require(it.matches(Regex("[a-z0-9-]+"))) }
    private val preferences get()=context.getSharedPreferences("matrix-$run",0)
    private val name get()="M"+run.replace("-", "").take(12)
    private val today get()=LocalDate.now(ZoneOffset.ofHours(-3)).toString()
    private fun fingerprint(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun geometry() {
        val expectedApi=InstrumentationRegistry.getArguments().getString("matrixApi")!!.toInt()
        assertEquals(expectedApi,android.os.Build.VERSION.SDK_INT)
        val expected=InstrumentationRegistry.getArguments().getString("matrixOrientation")
        assertEquals(if(expected=="portrait") android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            context.resources.configuration.orientation)
        val metrics=DisplayMetrics()
        @Suppress("DEPRECATION") compose.activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        val dimensions=InstrumentationRegistry.getArguments().getString("matrixDp")!!.split('x').map(String::toInt)
        assertEquals(dimensions[0],(metrics.widthPixels/metrics.density).toInt())
        assertEquals(dimensions[1],(metrics.heightPixels/metrics.density).toInt())
    }
    private fun mounted(restore:Boolean=false,block:()->Unit) {
        assertEquals("http://10.0.2.2:8081",lvluplife.BuildConfig.API_BASE_URL)
        val app=FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setApplicationId("1:123:android:abcdef")
            .setApiKey("synthetic-emulator-api-key").setProjectId("demo-lvluplife").build(),"matrix-$run")
        val auth=FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2",9099)
        val source=FirebaseSdkIdentitySource(auth)
        fun encode(value:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val subject="synthetic-matrix-$run"
        val jwt=encode("""{"alg":"none"}""")+"."+encode("""{"sub":"$subject","email":"$subject@example.test","email_verified":true,"iss":"https://accounts.google.com","aud":"synthetic-client"}""")+"."
        val google=object:GoogleSignIn {
            override suspend fun signIn():SignInOutcome = try {
                if(source.uid()==null) source.exchange(GoogleIdToken(jwt))
                val token=source.session(true)
                if(token==null || !source.googleUser()) SignInOutcome.Denied else SignInOutcome.Authenticated(token)
            } catch(failure:Exception) {
                // Class names only: retain a useful diagnostic without exporting credentials.
                File(context.cacheDir,"matrix-$run-auth-failure.txt").writeText(
                    generateSequence<Throwable>(failure) { it.cause }.take(8).joinToString("\n") { it.javaClass.name })
                SignInOutcome.Unavailable
            }
            override suspend fun restore()=if(restore) signIn() else SignInOutcome.Cancelled
            override suspend fun signOut() { source.signOut() }
        }
        try {
            compose.runOnIdle { graph.session.logout();compose.activity.setContent { LvlUpTheme {
                AppNavigation(graph.session,google,graph.accounts,graph.records,graph.progress,graph.activities,graph.history,graph.corrections,graph.resets)
            } } }
            geometry();block()
        } catch(failure:Throwable) {
            runCatching {
                capture("failure-live")
                File(context.cacheDir,"matrix-$run-tree.txt").writeText(compose.onRoot().printToString())
                val diagnostic=JSONObject().put("deviceNow",java.time.Instant.now().toString())
                diagnostic.put("records",org.json.JSONArray(history().items.map { item -> JSONObject().put("id",item.id)
                    .put("canCorrect",item.canCorrect).put("createdAt",item.createdAt.toString()) }))
                File(context.cacheDir,"matrix-$run-diagnostic.json").writeText(diagnostic.toString())
            }
            throw failure
        } finally {
            compose.runOnIdle { compose.activity.setContent {} }
            app.delete()
        }
    }
    private fun awaitText(text:String):SemanticsNodeInteraction {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(text)
    }
    private fun visible(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        try { node.performScrollTo() } catch(_:AssertionError) { /* A non-scrollable dialog/header must already be displayed. */ }
        // Network-driven route changes can replace the matching input with the final label.
        // Require the complete bounds once the transition settles, keeping the same clipping oracle.
        compose.waitUntil(30_000) { runCatching {
            node.assertIsDisplayed()
            val whole=node.getUnclippedBoundsInRoot();val clipped=node.getBoundsInRoot()
            kotlin.math.abs((whole.bottom-whole.top).value-(clipped.bottom-clipped.top).value)<=1f &&
                kotlin.math.abs((whole.right-whole.left).value-(clipped.right-clipped.left).value)<=1f
        }.getOrDefault(false) }
        node.assertIsDisplayed()
        val whole=node.getUnclippedBoundsInRoot();val clipped=node.getBoundsInRoot()
        assertEquals("Control/text must not be vertically clipped",(whole.bottom-whole.top).value,(clipped.bottom-clipped.top).value,1f)
        assertEquals("Control/text must not be horizontally clipped",(whole.right-whole.left).value,(clipped.right-clipped.left).value,1f)
        return node
    }
    private fun enabled(node:SemanticsNodeInteraction):SemanticsNodeInteraction {
        compose.waitUntil(30_000) { runCatching { !node.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }.getOrDefault(false) }
        return node.assertIsEnabled()
    }
    private fun tap(text:String) { enabled(visible(awaitText(text))).performTouchInput { click() };compose.waitForIdle() }
    private fun awaitTag(tag:String):SemanticsNodeInteraction {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithTag(tag)
    }
    private fun tapTag(tag:String) {
        enabled(visible(awaitTag(tag))).performTouchInput { click() };compose.waitForIdle()
    }
    private fun expect(text:String) { visible(awaitText(text)) }
    private fun input(label:String,value:String) {
        visible(awaitText(label)).performTextReplacement(value);hideKeyboard()
    }
    private fun hideKeyboard() {
        compose.runOnIdle { (compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0) }
        compose.waitForIdle()
    }
    @Suppress("UNUSED_PARAMETER")
    private fun capture(label:String,dialog:Boolean=false) {
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.cacheDir,"matrix-$run-$label.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    private fun uid()=requireNotNull(graph.session.current()).uid
    private fun progress()=runBlocking { requireNotNull(graph.progress).load(uid()) }
    private fun history()=runBlocking { requireNotNull(graph.history).load(uid(),HistorySelection(HistoryPeriod.day,today)) }
    private fun stable(view:RecordProgressView)=JSONObject().put("cycleId",view.cycleId).put("classId",view.classId).put("stage",view.stage)
        .put("generalLevel",view.generalLevel).put("attributes",JSONObject().also { root ->
            view.attributes.toSortedMap().forEach { (id,value) -> root.put(id,JSONObject().put("level",value.level).put("xp",value.xp)) }
        }).toString()
    private fun draft()=runBlocking(Dispatchers.IO) { PendingDatabase.open(context).use { it.draftFor(uid())?.content } }
    private fun checkpoint(stage:String,state:String) {
        instrumentation.sendStatus(0,Bundle().apply {
            putString("matrixStage",stage);putString("matrixCell",cell);putString("matrixPid",Process.myPid().toString())
            putString("matrixOwner",fingerprint(uid()));putString("matrixState",fingerprint(state))
        })
    }
    @Test @ProcessStage fun criticalFlowsBeforeClosingProcess() = mounted {
        tap("Continuar con Google")
        expect("Nombre de usuario");input("Nombre de usuario",name);tap("Crear personaje")
        expect("Novato");expect(name);capture("profile")
        tapTag("quick-meal");tapTag("quick-save");expect("Actividad guardada")
        tapTag("quick-strength")
        compose.onNodeWithTag("quick-duration-input").assertIsFocused().performTextReplacement("0:30");hideKeyboard()
        tapTag("quick-save");expect("Actividad guardada")
        val initial=history();assertEquals(2,initial.items.size)
        val training=initial.items.single { it.activityId=="strength" };val meal=initial.items.single { it.activityId=="meal" }
        tap("Historial");expect("Entrenamiento de fuerza")
        tapTag("history-edit-${training.id}");input("Minutos","45");tap("Guardar cambios");expect("Cambios guardados")
        tapTag("history-delete-${meal.id}");expect("Eliminar registro");capture("delete",dialog=true)
        tap("Confirmar eliminación");expect("Registro eliminado")
        input("Fecha del período","fecha-inválida");tap("Consultar período");expect("Revisá la fecha del período");capture("history-error")
        input("Fecha del período",today);tap("Consultar período");expect("Entrenamiento de fuerza")
        tap("Volver");expect(name)
        tap("Crear actividad personalizada");input("Nombre","Actividad matriz");input("Categoría","Rutina")
        tap("Guardar definición");expect("Actividad personalizada guardada")
        tap("Clases");expect("Clases y etapas")
        awaitTag("class-catalog").performScrollToIndex(2)
        visible(compose.onNodeWithTag("class-guerrero-stage-2-status")).assertTextEquals("Bloqueada");capture("classes")
        tap("Volver a principal")
        val old=progress().cycleId
        tapTag("reset-open");expect("¿Reiniciar tu personaje?");capture("reset",dialog=true)
        tapTag("reset-confirm");expect("Reinicio confirmado")
        val reset=progress();assertNotEquals(old,reset.cycleId);assertEquals("novato",reset.classId);assertEquals(1,reset.generalLevel)
        assertTrue(reset.attributes.values.all { it.level==1 && it.xp==0 })
        val retained=history().items.single();assertEquals(training.id,retained.id);assertEquals(45,retained.durationMinutes);assertEquals(2L,retained.version)
        assertFalse(retained.canCorrect)
        assertEquals(1,runBlocking { requireNotNull(graph.activities).list(uid()).size })
        tap("Registrar actividad");input("Fecha","fecha-inválida");tap("Guardar actividad");expect("Revisá los datos ingresados.")
        input("Fecha",today);input("Horas","0");input("Minutos","25");tap("Volver")
        compose.waitUntil(10_000) { draft()?.let { JSONObject(it).optJSONObject("recordDraft")?.optString("minutes")=="25" }==true }
        val state=stable(reset)
        assertTrue(preferences.edit().putInt("pid",Process.myPid()).putString("state",state).putString("record",retained.id)
            .putString("draft",fingerprint(requireNotNull(draft()))).putString("owner",fingerprint(uid())).commit())
        capture("confirmed-before-close");checkpoint("before-close",state)
    }
    @Test @ProcessStage fun confirmedDataAndDraftSurviveProcessReopen() = mounted(restore=true) {
        assertNotEquals(preferences.getInt("pid",-1),Process.myPid())
        expect("Novato");expect(name);assertEquals(preferences.getString("owner",null),fingerprint(uid()))
        assertEquals(preferences.getString("state",null),stable(progress()))
        assertEquals(preferences.getString("draft",null),fingerprint(requireNotNull(draft())))
        tap("Registrar actividad");expect("Minutos");compose.onNodeWithText("Minutos").assertTextContains("25");capture("draft-reopened")
        tap("Volver");tap("Historial");expect("Entrenamiento de fuerza")
        val item=history().items.single();assertEquals(preferences.getString("record",null),item.id);assertEquals(45,item.durationMinutes);assertFalse(item.canCorrect)
        compose.onNodeWithTag("history-edit-${item.id}").assertDoesNotExist();capture("history-reopened")
        assertEquals(1,runBlocking { requireNotNull(graph.activities).list(uid()).size })
        tap("Cerrar sesión");expect("Continuar con Google");compose.onNodeWithText(name).assertDoesNotExist()
        tap("Continuar con Google");expect(name);expect("Novato")
        assertEquals(preferences.getString("state",null),stable(progress()));capture("reopened")
        checkpoint("reopened",requireNotNull(preferences.getString("state",null)))
    }
}
