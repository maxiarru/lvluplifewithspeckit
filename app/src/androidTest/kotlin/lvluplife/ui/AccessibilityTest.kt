package lvluplife.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.pow
import kotlinx.coroutines.*
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.*
import lvluplife.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/** Rendered color pairs plus actual gestures through RecordFlow; no domain formulas in the fixture. */
class AccessibilityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var h: Harness
    private lateinit var p: Palette
    private var mode="light"
    private data class Palette(val text:Int,val muted:Int,val surface:Int,val button:Int,val buttonText:Int,
        val disabled:Int,val card:Int,val dialog:Int,val selected:Int,val selectedText:Int,val chipText:Int)
    companion object {
        @JvmStatic @BeforeClass fun resetReports() {
            val cache=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            File(cache,"t079-contrast.jsonl").delete();File(cache,"t079-gestures.jsonl").delete()
        }
    }
    private class Harness(scope: CoroutineScope) {
        val session=SessionController().apply { authenticate(SessionToken("synthetic-a","synthetic-accessibility-token")) }
        var online by mutableStateOf(true)
        var outcome=PendingState.CONFIRMED
        var gate: CompletableDeferred<Unit>?=null
        val submissions=mutableListOf<Triple<String,String,RecordDraft>>()
        val saved=mutableListOf<RecordDraft>()
        var retries=0
        val records=object:RecordFlowRecords {
            override val online get()=this@Harness.online
            override suspend fun catalog(uid:String)=listOf(RecordOption("strength","Entrenamiento de fuerza",true),RecordOption("meal","Comida saludable",false),RecordOption("sleep","Dormir",true))
            override suspend fun draft(uid:String):RecordDraft?=null
            override suspend fun saveDraft(uid:String,draft:RecordDraft) { saved+=draft }
            override suspend fun pending(uid:String)=emptyList<OperationSnapshot>()
            override suspend fun submit(uid:String,intentId:String,draft:RecordDraft):OperationSnapshot {
                submissions+=Triple(uid,intentId,draft);gate?.await();return OperationSnapshot(OperationRef(uid,intentId),outcome)
            }
            override suspend fun retry(ref:OperationRef):OperationSnapshot { retries++;return OperationSnapshot(ref,PendingState.CONFIRMED) }
        }
        val recorder=RecordFormController(session,records,scope)
        val google=object:GoogleSignIn { override suspend fun signIn()=SignInOutcome.Cancelled;override suspend fun signOut()=Unit }
        val access=AccessController(session,google,AccessAccounts { ProfileLookup.Required },scope)
        val custom=CustomActivityController(session,object:CustomActivityFlowActivities {
            override val online get()=true
            override suspend fun list(uid:String)=emptyList<CustomActivityOption>()
            override suspend fun draft(uid:String):CustomActivityDraft?=null
            override suspend fun saveDraft(uid:String,draft:CustomActivityDraft)=Unit
            override suspend fun pending(uid:String)=emptyList<OperationSnapshot>()
            override suspend fun submit(uid:String,intentId:String,draft:CustomActivityDraft):OperationSnapshot=error("Unused mutation")
            override suspend fun retry(ref:OperationRef):OperationSnapshot=error("Unused mutation")
        },scope)
        val corrections=CorrectionController(session,object:HistoryFlowCorrections {
            override val online get()=true
            override suspend fun draft(uid:String):CorrectionDraft?=null
            override suspend fun saveDraft(uid:String,draft:CorrectionDraft)=Unit
            override suspend fun pending(uid:String)=emptyList<OperationSnapshot>()
            override suspend fun edit(uid:String,intentId:String,draft:CorrectionDraft):OperationSnapshot=error("Unused mutation")
            override suspend fun delete(uid:String,intentId:String,item:HistoryItem):OperationSnapshot=error("Unused mutation")
            override suspend fun retry(ref:OperationRef):OperationSnapshot=error("Unused mutation")
        },scope)
        val history=HistoryController(session,HistoryFlowHistory { _,_-> HistoryPage(1,"2026-11-12","2026-11-13",emptyList()) },scope)
        val reset=ResetController(session,object:ResetFlowOperations {
            override val online get()=true
            override suspend fun pending(uid:String)=emptyList<OperationSnapshot>()
            override suspend fun submit(uid:String,intentId:String,cycleId:String,confirmed:Boolean):OperationSnapshot=error("Unused mutation")
            override suspend fun retry(ref:OperationRef):OperationSnapshot=error("Unused mutation")
        },scope)
    }
    private fun mount(dark:Boolean=false,content:@Composable (Harness)->Unit) {
        mode=if(dark) "dark" else "light"
        compose.runOnIdle { compose.activity.setContent { LvlUpTheme(dark) {
            val scope=rememberCoroutineScope();val harness=remember { Harness(scope) }
            val colors=MaterialTheme.colorScheme
            val palette=Palette(colors.onSurface.toArgb(),colors.onSurfaceVariant.toArgb(),colors.surface.toArgb(),
                colors.primary.toArgb(),colors.onPrimary.toArgb(),colors.surfaceContainerHighest.toArgb(),CardDefaults.cardColors().containerColor.toArgb(),
                AlertDialogDefaults.containerColor.toArgb(),colors.secondaryContainer.toArgb(),colors.onSecondaryContainer.toArgb(),colors.onSurfaceVariant.toArgb())
            SideEffect { h=harness;p=palette }
            content(harness)
        } } };compose.waitForIdle()
    }
    private fun progress()=RecordProgressView(10,"confirmed-cycle","2026-11-12",2,"guerrero",1,
        listOf("STR","DEX","CON","INT","WIT","MEN").associateWith { RecordAttributeView(1,6) },"Guerrero")
    private fun pending(error:ApiFailureCode?=null)=OperationSnapshot(OperationRef("synthetic-a",UUID.randomUUID().toString()),PendingState.UNKNOWN,error)
    private fun luminance(color:Int):Double {
        fun channel(shift:Int):Double { val c=((color ushr shift) and 255)/255.0;return if(c<=0.04045)c/12.92 else ((c+0.055)/1.055).pow(2.4) }
        return 0.2126*channel(16)+0.7152*channel(8)+0.0722*channel(0)
    }
    private fun measure(text:String,foreground:Int,background:Int,key:String,scroll:Boolean=false,node:SemanticsNodeInteraction=compose.onNodeWithText(text)) {
        if(scroll)node.performScrollTo()
        node.assertIsDisplayed()
        val bitmap=node.captureToImage().asAndroidBitmap();val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val fg=pixels.count { it==foreground };val bg=pixels.count { it==background }
        assertTrue("$mode/$key foreground not rendered",fg>0);assertTrue("$mode/$key background not rendered",bg>0)
        val a=luminance(foreground);val b=luminance(background);val ratio=(maxOf(a,b)+0.05)/(minOf(a,b)+0.05)
        assertTrue("$mode/$key ratio=$ratio",ratio>=4.5)
        File(context.cacheDir,"t079-contrast.jsonl").appendText(JSONObject().put("palette",mode).put("pair",key)
            .put("foreground",String.format("#%06X",foreground and 0xffffff)).put("background",String.format("#%06X",background and 0xffffff))
            .put("ratio",ratio).put("foregroundPixels",fg).put("backgroundPixels",bg).toString()+"\n")
    }
    private fun capture(key:String,dialog:Boolean=false) {
        val node=if(dialog) compose.onNode(isDialog()) else compose.onRoot()
        File(context.cacheDir,"t079-$mode-$key.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun accessAndProfileStatesHaveContrastInBothPalettes() {
        for(dark in listOf(false,true)) {
            for(busy in listOf(false,true)) {
                mount(dark) { AccessScreen(busy,"No pudimos iniciar sesión",{}) }
                measure("Iniciá sesión para continuar",p.text,p.surface,"access-instruction-$busy")
                measure("No pudimos iniciar sesión",p.text,p.surface,"access-error-$busy")
                measure("Continuar con Google",if(busy)p.text else p.buttonText,if(busy)p.disabled else p.button,"access-button-$busy")
                if(busy)measure("Iniciando sesión…",p.text,p.surface,"access-loading")
                capture("access-$busy")
                mount(dark) { ProfileScreen(AccessView(username="Jugador",busy=busy,error="Revisá tu nombre"),it.access) }
                measure("Jugador",p.text,p.surface,"profile-value-$busy")
                measure("Nombre de usuario",if(busy)p.text else p.muted,p.surface,"profile-label-$busy")
                measure("Revisá tu nombre",p.text,p.surface,"profile-error-$busy")
                measure("Crear personaje",if(busy)p.text else p.buttonText,if(busy)p.disabled else p.button,"profile-save-$busy")
                if(busy)measure("Procesando nombre…",p.text,p.surface,"profile-loading")
                capture("profile-$busy")
            }
            mount(dark) { ProfileScreen(AccessView(username="Jugador",pending=pending()),it.access) }
            measure("Confirmación pendiente",p.text,p.surface,"profile-pending");capture("profile-pending")
        }
    }
    @Test fun homeAndRecordStatesHaveContrastAndExplicitStatusInBothPalettes() {
        for(dark in listOf(false,true)) {
            for(view in listOf(ProgressView(loading=true),ProgressView(error=ApiFailureCode.NETWORK_ERROR,loading=false),
                ProgressView(progress(),loading=false),ProgressView(progress(),loading=true,stale=true))) {
                mount(dark) { HomeScreen(view,{},{}) }
                val label=if(view.value==null) if(view.loading) "Cargando progreso…" else "No se pudo obtener el progreso" else "Guerrero"
                measure(label,p.text,p.surface,"home-$label")
                if(view.stale) { measure("Mostrando el último progreso confirmado",p.text,p.surface,"home-stale");measure("Actualizar progreso",p.text,p.disabled,"home-disabled") }
                capture("home-${if(view.stale) "stale" else if(view.loading) "loading" else if(view.error!=null) "error" else "confirmed"}")
            }
            val options=listOf(RecordOption("meal","Comida saludable",false),RecordOption("sleep","Dormir",true))
            for(locked in listOf(false,true)) {
                mount(dark) { RecordScreen(RecordFormView(RecordDraft("sleep","2026-11-12","7","30"),options,open=true,busy=locked,
                    message="Revisá los datos ingresados."),it.recorder,true) }
                measure("7",p.text,p.surface,"duration-hours-$locked");measure("30",p.text,p.surface,"duration-minutes-$locked")
                measure("Dormir",if(locked)p.text else p.selectedText,if(locked)p.disabled else p.selected,"record-selected-$locked")
                measure("Comida saludable",if(locked)p.text else p.chipText,if(locked)p.disabled else p.surface,"record-unselected-$locked")
                compose.onNodeWithText("Dormir").assertIsSelected()
                measure("Guardar actividad",if(locked)p.text else p.buttonText,if(locked)p.disabled else p.button,"record-save-$locked",scroll=true)
                measure("Revisá los datos ingresados.",p.text,p.surface,"record-rejected-$locked",scroll=true);capture("record-$locked")
                if(locked)measure("Procesando actividad…",p.text,p.surface,"record-loading",scroll=true)
            }
            mount(dark) { RecordScreen(RecordFormView(RecordDraft("meal","2026-11-12","0",""),options,open=true,busy=false),it.recorder,true) }
            measure("Comida saludable",p.selectedText,p.selected,"meal-selected");compose.onNodeWithText("Horas").assertDoesNotExist();capture("meal")
            mount(dark) { RecordScreen(RecordFormView(options=options,busy=false,pending=pending(ApiFailureCode.FORBIDDEN)),it.recorder,true) }
            measure("Confirmación pendiente",p.text,p.surface,"record-pending");measure("Reintentar operación",p.text,p.disabled,"record-disabled-retry");capture("record-pending")
        }
    }
    @Test fun customSelectionDisabledControlsAndListStatesHaveContrastInBothPalettes() {
        for(dark in listOf(false,true)) for(locked in listOf(false,true)) {
            mount(dark) { CustomActivityScreen(CustomActivityView(draft=CustomActivityDraft("Lectura","Rutina","STR"),open=true,busy=locked,
                listLoaded=true,listError=!locked,message="Revisá los datos de la actividad"),it.custom,true) }
            measure("Lectura",p.text,p.surface,"custom-value-$locked")
            val primary=compose.onNodeWithTag("custom-primary-STR");primary.assertIsSelected()
            primary.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,if(locked) "Seleccionada · No disponible" else "Seleccionada"))
            measure("Sin secundario",if(locked)p.text else p.selectedText,if(locked)p.disabled else p.selected,"custom-selection-$locked",scroll=true)
            compose.onNodeWithTag("custom-secondary-STR").assertIsNotEnabled()
            measure("Guardar definición",if(locked)p.text else p.buttonText,if(locked)p.disabled else p.button,"custom-save-$locked",scroll=true)
            measure("Revisá los datos de la actividad",p.text,p.surface,"custom-rejected-$locked",scroll=true)
            if(!locked) measure("No se pudieron cargar tus actividades",p.text,p.surface,"custom-list-error",scroll=true)
            else measure("Todavía no tenés actividades propias",p.text,p.surface,"custom-empty",scroll=true)
            if(locked)measure("Procesando definición…",p.text,p.surface,"custom-processing",scroll=true)
            capture("custom-$locked")
        }
    }
    @Test fun historyCorrectionAndTheirPendingStatesHaveContrastInBothPalettes() {
        val item=HistoryItem("synthetic-record","cycle","strength","Entrenamiento","2026-11-12",30,Instant.parse("2026-11-12T12:00:00Z"),1,true)
        for(dark in listOf(false,true)) {
            for(state in listOf("loading","empty","stale","confirmed")) {
                mount(dark) { HistoryScreen(HistoryView(page=if(state=="loading")null else HistoryPage(1,"2026-11-12","2026-11-13",if(state=="empty")emptyList() else listOf(item)),
                    loading=state=="loading",stale=state=="stale",error=if(state=="stale")ApiFailureCode.NETWORK_ERROR else null),it.history,true,it.corrections,CorrectionView(busy=false)) }
                val label=when(state) { "loading"->"Cargando historial…";"empty"->"No hay registros en este período";"stale"->"Mostrando el último historial confirmado";else->"Entrenamiento" }
                measure(label,p.text,if(state=="confirmed")p.card else p.surface,"history-$state",scroll=true)
                measure("Día",p.selectedText,p.selected,"history-selected-$state",scroll=true)
                compose.onNodeWithText("Día").assertIsSelected();capture("history-$state")
            }
            for(locked in listOf(false,true)) {
                mount(dark) { EditRecordScreen(CorrectionView(draft=CorrectionDraft(item.id,"cycle",1,"strength","2026-11-12","0","30"),
                    options=listOf(RecordOption("strength","Entrenamiento",true)),busy=locked,message="El registro cambió. Revisá los datos actuales."),it.corrections,true) }
                measure("Editar registro",p.text,p.surface,"edit-title-$locked")
                measure("Guardar cambios",if(locked)p.text else p.buttonText,if(locked)p.disabled else p.button,"edit-save-$locked",scroll=true)
                measure("El registro cambió. Revisá los datos actuales.",p.text,p.surface,"edit-stale-$locked",scroll=true);capture("edit-$locked")
                if(locked)measure("Procesando corrección…",p.text,p.surface,"edit-processing",scroll=true)
            }
            mount(dark) { Column { CorrectionStatus(CorrectionView(busy=false,pending=pending(ApiFailureCode.AUTH_REQUIRED),message="Volvé a iniciar sesión para resolver la operación"),it.corrections,true) } }
            measure("Confirmación pendiente",p.text,p.surface,"correction-pending");measure("Reintentar corrección",p.text,p.disabled,"correction-disabled");capture("correction-pending")
        }
    }
    @Test fun customLoadingAndPendingHaveReadableStatusAndDisableNewDefinitions() {
        for(dark in listOf(false,true)) {
            mount(dark) { CustomActivityScreen(CustomActivityView(),it.custom,true) }
            measure("Cargando actividades propias…",p.text,p.surface,"custom-loading");capture("custom-loading")
            mount(dark) { CustomActivityScreen(CustomActivityView(busy=false,pending=pending(ApiFailureCode.FORBIDDEN)),it.custom,true) }
            compose.onNodeWithText("Crear actividad personalizada").assertIsNotEnabled()
            measure("Confirmación pendiente",p.text,p.surface,"custom-pending")
            measure("Reintentar definición",p.text,p.disabled,"custom-disabled-retry");capture("custom-pending")
        }
    }
    @Test fun classLocksAndLoadingErrorStaleStatesRemainExplicitInBothPalettes() {
        val entries=ClassAppearance.all.groupBy { it.classId }.map { (id,images) -> ClassCatalogEntry(id,id,images.map {
            ClassStageImage(it.stage,it.resourceId,mapOf(1 to 1,2 to 15,3 to 35).getValue(it.stage)) }) }
        for(dark in listOf(false,true)) {
            mount(dark) { ClassCatalogScreen(ClassCatalogView(entries,loading=false),14,false,{},{}) }
            compose.onNodeWithTag("class-catalog").performScrollToIndex(0)
            measure("Desbloqueada",p.text,p.card,"class-unlocked",node=compose.onNodeWithTag("class-novato-stage-1-status"))
            compose.onNodeWithTag("class-novato-stage-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Desbloqueada"))
            compose.onNodeWithTag("class-catalog").performScrollToIndex(2)
            measure("Bloqueada",p.text,p.card,"class-locked",node=compose.onNodeWithTag("class-guerrero-stage-2-status"))
            compose.onNodeWithTag("class-guerrero-stage-2").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Bloqueada"));capture("classes")
            mount(dark) { ClassCatalogScreen(ClassCatalogView(loading=true),14,false,{},{}) }
            measure("Cargando clases…",p.text,p.surface,"class-loading");capture("classes-loading")
            mount(dark) { ClassCatalogScreen(ClassCatalogView(entries,loading=false,error=ApiFailureCode.NETWORK_ERROR,stale=true),14,true,{},{}) }
            measure("Mostrando el último catálogo confirmado",p.text,p.surface,"class-stale")
            measure("No pudimos actualizar las clases",p.text,p.surface,"class-error")
            measure("Reintentar clases",p.buttonText,p.button,"class-retry");capture("classes-stale")
        }
    }
    @Test fun resetAndDeletionDialogsKeepReadableTextAndExplicitActionsInBothPalettes() {
        for(dark in listOf(false,true)) {
            mount(dark) { ResetScreen(ResetView(busy=false,ready=true,dialogCycle="cycle"),it.reset,"cycle",true) }
            measure("¿Reiniciar tu personaje?",p.text,p.dialog,"reset-title")
            measure("Se reinicia tu progreso. Se conservan tu cuenta, nombre de usuario, actividades personalizadas e historial.",p.muted,p.dialog,"reset-explanation")
            measure("Reiniciar",p.buttonText,p.button,"reset-confirm");measure("Cancelar",p.button,p.dialog,"reset-cancel");capture("reset-dialog",dialog=true)
            mount(dark) { ResetScreen(ResetView(busy=false,ready=true,pending=pending(ApiFailureCode.FORBIDDEN),error=ApiFailureCode.FORBIDDEN,
                message="Acceso denegado. La operación sigue pendiente."),it.reset,"cycle",true) }
            measure("Reiniciar progresión",p.text,p.disabled,"reset-disabled");measure("Confirmación pendiente",p.text,p.surface,"reset-pending")
            measure("Reintentar reinicio",p.text,p.disabled,"reset-retry-disabled");capture("reset-pending")
            mount(dark) { ResetScreen(ResetView(busy=true,ready=true,pending=pending()),it.reset,"cycle",true) }
            measure("Procesando reinicio…",p.text,p.surface,"reset-processing");capture("reset-processing")
            mount(dark) { AlertDialog(onDismissRequest={},title={ Text("Eliminar registro") },text={ Text("Se quitará del historial y se recalculará tu progreso.") },
                confirmButton={ ActionTextButton({},enabled=false) { Text("Confirmar eliminación") } },dismissButton={ ActionTextButton({}) { Text("Cancelar") } }) }
            measure("Eliminar registro",p.text,p.dialog,"delete-title");measure("Se quitará del historial y se recalculará tu progreso.",p.muted,p.dialog,"delete-explanation")
            measure("Confirmar eliminación",p.text,p.disabled,"delete-disabled");measure("Cancelar",p.button,p.dialog,"delete-cancel");capture("delete-dialog",dialog=true)
        }
    }
    private fun frequent(id:String,minutes:Int?,typed:String?=null) {
        mount { fixture -> SessionHost(fixture.session) { _ -> RecordFlow(fixture.session,fixture.records,RecordFlowProgress { progress() }) } }
        val gestures=mutableListOf<String>()
        fun tap(tag:String) { compose.onNodeWithTag(tag).assertIsDisplayed().performClick();gestures+=tag }
        if(id=="meal")capture("principal-quick")
        measure(when(id) { "meal"->"Comida";"sleep"->"Dormir";else->"Entrenar" },p.buttonText,p.button,"quick-$id-button")
        tap("quick-$id")
        if(minutes!=null) { compose.onNodeWithTag("quick-save").assertIsNotEnabled();measure("Guardar",p.text,p.disabled,"quick-$id-disabled-save") }
        if(typed!=null) {
            compose.onNodeWithTag("quick-duration-input").assertIsFocused().performTextInput(typed);gestures+="type-duration-continuously"
        } else if(minutes!=null) { tap("quick-duration-$minutes");compose.onNodeWithTag("quick-duration-$minutes").assertIsSelected() }
        if(minutes!=null)measure(typed ?: "${minutes/60}:${(minutes%60).toString().padStart(2,'0')}",p.text,p.dialog,"quick-$id-duration")
        capture("quick-$id-${if(typed==null) "preset" else "typed"}",dialog=true);tap("quick-save")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Actividad guardada").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(gestures.size<=3);assertEquals(1,h.submissions.size)
        val (uid,intent,draft)=h.submissions.single();assertEquals("synthetic-a",uid);UUID.fromString(intent)
        assertEquals(id,draft.activityId);assertEquals(LocalDate.now(ZoneOffset.ofHours(-3)).toString(),draft.date)
        if(minutes!=null)assertEquals(minutes,draft.hours.toInt()*60+draft.minutes.toInt()) else assertEquals("",draft.minutes)
        File(context.cacheDir,"t079-gestures.jsonl").appendText(JSONObject().put("activityId",id).put("count",gestures.size).put("gestures",JSONArray(gestures))
            .put("mode",if(typed==null) "preset" else "typed").put("date",draft.date).put("hours",draft.hours).put("minutes",draft.minutes).put("submissions",h.submissions.size).toString()+"\n")
    }
    @Test fun savedMealUsesTwoGesturesFromPrincipal()=frequent("meal",null)
    @Test fun savedTrainingUsesThreeGesturesFromPrincipal()=frequent("strength",30)
    @Test fun savedSleepUsesThreeGesturesFromPrincipal()=frequent("sleep",420)
    @Test fun trainingWithArbitraryDurationNeedsNoExtraFieldSelectionGesture()=frequent("strength",75,"1:15")
    @Test fun sleepWithArbitraryDurationNeedsNoExtraFieldSelectionGesture()=frequent("sleep",455,"7:35")
    @Test fun quickDurationValidationRetainsInputAndNeverSubmitsInvalidOrCancelledValues() {
        mount { fixture -> SessionHost(fixture.session) { _ -> RecordFlow(fixture.session,fixture.records,RecordFlowProgress { progress() }) } }
        compose.onNodeWithTag("quick-sleep").performClick()
        for(value in listOf("0:00","24:01","-1:00","1:60","1.5")) {
            compose.onNodeWithTag("quick-duration-input").performTextReplacement(value)
            compose.onNodeWithTag("quick-save").assertIsNotEnabled()
            compose.onNodeWithTag("quick-duration-input").assertTextContains(value)
        }
        capture("quick-invalid-duration",dialog=true)
        measure("Ingresá una duración de 0:01 a 24:00",p.muted,p.dialog,"quick-invalid-duration")
        compose.onNodeWithTag("quick-duration-input").performTextReplacement("24:00")
        compose.onNodeWithTag("quick-save").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick();assertTrue(h.submissions.isEmpty());assertTrue(h.saved.isEmpty())
    }
    @Test fun quickCancelAndOfflineNeverSaveOrSubmit() {
        mount { fixture -> SessionHost(fixture.session) { _ -> RecordFlow(fixture.session,fixture.records,RecordFlowProgress { progress() }) } }
        compose.onNodeWithTag("quick-sleep").performClick();compose.onNodeWithText("Cancelar").performClick()
        assertTrue(h.saved.isEmpty());assertTrue(h.submissions.isEmpty())
        compose.runOnIdle { h.online=false };compose.onNodeWithTag("quick-meal").assertIsNotEnabled().performClick()
        assertTrue(h.saved.isEmpty());assertTrue(h.submissions.isEmpty())
    }
    @Test fun uncertainQuickRecordDisablesNewSubmissionsUntilOriginalRetryResolves() {
        mount { fixture -> SessionHost(fixture.session) { _ -> RecordFlow(fixture.session,fixture.records,RecordFlowProgress { progress() }) } }
        compose.runOnIdle { h.outcome=PendingState.UNKNOWN }
        compose.onNodeWithTag("quick-meal").performClick();compose.onNodeWithTag("quick-save").performClick()
        compose.onNodeWithText("Confirmación pendiente").assertIsDisplayed()
        for(id in listOf("meal","strength","sleep"))compose.onNodeWithTag("quick-$id").assertIsNotEnabled()
        compose.onNodeWithText("Reintentar operación").performClick()
        compose.onNodeWithText("Actividad guardada").assertIsDisplayed();assertEquals(1,h.submissions.size);assertEquals(1,h.retries)
    }
}
