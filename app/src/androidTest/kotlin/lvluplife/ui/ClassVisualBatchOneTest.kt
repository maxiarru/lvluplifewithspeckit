package lvluplife.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import lvluplife.MainActivity
import lvluplife.application.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real compiled PNGs and Home renderer. Preview sizes are QA surfaces, not the future catalogue screen. */
class ClassVisualBatchOneTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = linkedMapOf("novato" to "Novato", "guerrero" to "Guerrero", "explorador" to "Explorador",
        "guardian" to "Guardián", "sabio" to "Sabio", "monje" to "Monje", "mistico" to "Místico",
        "maestro-de-armas" to "Maestro de Armas", "paladin" to "Paladín", "guerrero-arcano" to "Guerrero Arcano",
        "luchador-monje" to "Luchador Monje", "guerrero-mistico" to "Guerrero Místico", "agil-guardian" to "Ágil Guardián")
    private fun label(visual: ClassVisual) = "${names.getValue(visual.classId)} · Etapa ${listOf("I", "II", "III")[visual.stage-1]}"
    private fun progress(visual: ClassVisual) = RecordProgressView(10, "synthetic-visual-cycle", "2026-11-11", 35,
        visual.classId, visual.stage, listOf("STR", "DEX", "CON", "INT", "WIT", "MEN").associateWith { RecordAttributeView(1, 0) },
        names.getValue(visual.classId))

    @Test fun all37BindingsResolveToDistinctFullSizePngResources() {
        val entries = ClassAppearance.batchOne
        assertEquals(37, entries.size)
        assertEquals(names.keys, entries.map { it.classId }.toSet())
        assertEquals(37, entries.map { it.resourceId }.toSet().size)
        assertEquals(37, entries.map { it.drawable }.toSet().size)
        for (id in names.keys) assertEquals(if (id=="novato") listOf(1) else listOf(1,2,3), entries.filter { it.classId==id }.map { it.stage })
        for (entry in entries) {
            assertEquals(entry.resourceId, context.resources.getResourceEntryName(entry.drawable))
            assertEquals("drawable", context.resources.getResourceTypeName(entry.drawable))
            assertEquals(entry.drawable, CurrentAppearance.image(entry.classId,entry.stage))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            context.resources.openRawResource(entry.drawable).use { BitmapFactory.decodeStream(it,null,bounds) }
            assertEquals(1122,bounds.outWidth); assertEquals(1402,bounds.outHeight)
            assertEquals("image/png",bounds.outMimeType)
        }
        assertNull(ClassAppearance.binding("novato",2)); assertNull(ClassAppearance.binding("novato",3))
        assertNull(ClassAppearance.binding("guerrero",0)); assertNull(ClassAppearance.binding("guerrero",4))
        assertNull(ClassAppearance.binding("unknown",1))
    }

    @Test fun homeDisplaysEachOfThe37AuditedBindingsWithoutFallback() {
        var visual by mutableStateOf(ClassAppearance.batchOne.first())
        compose.runOnIdle { compose.activity.setContent { MaterialTheme { HomeScreen(ProgressView(progress(visual),loading=false),{}) } } }
        for (entry in ClassAppearance.batchOne) {
            compose.runOnIdle { visual=entry }
            compose.onNodeWithContentDescription(label(entry)).assertIsDisplayed()
            compose.onNodeWithTag("character-class").assertTextEquals(names.getValue(entry.classId))
            compose.onNodeWithText("Apariencia no disponible").assertDoesNotExist()
        }
        saveCapture("t066-home-agil-guardian-iii.png")
    }

    @Test fun captureEveryClassAtHomeAndCataloguePreviewSizesWithoutCropping() {
        var classId by mutableStateOf("novato")
        compose.runOnIdle { compose.activity.setContent { MaterialTheme {
            Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(names.getValue(classId))
                Text("Vista de revisión · 88 dp / 160 dp")
                for (entry in ClassAppearance.batchOne.filter { it.classId==classId }) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(entry.drawable),label(entry)+" · 88 dp",Modifier.size(88.dp),contentScale=ContentScale.Fit)
                    Image(painterResource(entry.drawable),label(entry)+" · 160 dp",Modifier.size(160.dp),contentScale=ContentScale.Fit)
                }
            }
        } } }
        for (id in names.keys) {
            compose.runOnIdle { classId=id }
            for (entry in ClassAppearance.batchOne.filter { it.classId==id }) {
                compose.onNodeWithContentDescription(label(entry)+" · 88 dp").assertIsDisplayed()
                compose.onNodeWithContentDescription(label(entry)+" · 160 dp").assertIsDisplayed()
            }
            saveCapture("t066-preview-$id.png")
        }
    }
    private fun saveCapture(name: String) {
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
