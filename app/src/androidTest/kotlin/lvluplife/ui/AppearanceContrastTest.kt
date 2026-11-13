package lvluplife.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.pow
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/** WCAG sRGB pairs verified in actual glyph/background pixels, not antialiased edge averages. */
class AppearanceContrastTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private data class Palette(val text: Int,val surface: Int,val button: Int,val buttonText: Int,val card: Int)
    private lateinit var palette: Palette
    companion object {
        @JvmStatic @BeforeClass fun clearReport() {
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"t070-contrast.jsonl").delete()
        }
    }
    private fun mount(content: @Composable () -> Unit) {
        compose.runOnIdle { compose.activity.setContent { MaterialTheme {
            val colors=MaterialTheme.colorScheme
            val p=Palette(colors.onSurface.toArgb(),colors.surface.toArgb(),colors.primary.toArgb(),
                colors.onPrimary.toArgb(),CardDefaults.cardColors().containerColor.toArgb())
            SideEffect { palette=p }
            content()
        } } }
        compose.waitForIdle()
    }
    private fun progress()=RecordProgressView(12,"synthetic-final-cycle","2026-11-12",35,"guardian-de-la-sabiduria",3,
        listOf("STR","DEX","CON","INT","WIT","MEN").associateWith { RecordAttributeView(15,7) },"Guardián de la Sabiduría")
    private fun entries()=ClassAppearance.all.groupBy { it.classId }.map { (id,images) ->
        ClassCatalogEntry(id,id.replace('-',' ').replaceFirstChar { it.uppercase() },images.map {
            ClassStageImage(it.stage,it.resourceId,mapOf(1 to 1,2 to 15,3 to 35).getValue(it.stage))
        },ClassConditionFixtures.of(id))
    }
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value=((color ushr shift) and 255)/255.0
            return if(value<=0.04045) value/12.92 else ((value+0.055)/1.055).pow(2.4)
        }
        return 0.2126*channel(16)+0.7152*channel(8)+0.0722*channel(0)
    }
    private fun measure(node: SemanticsNodeInteraction,foreground: Int,background: Int,key: String) {
        val bitmap=node.captureToImage().asAndroidBitmap()
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val foregroundPixels=pixels.count { it==foreground }
        val backgroundPixels=pixels.count { it==background }
        assertTrue("$key: color de texto no observado",foregroundPixels>0)
        assertTrue("$key: fondo esperado no observado",backgroundPixels>0)
        val a=luminance(foreground);val b=luminance(background)
        val ratio=(maxOf(a,b)+0.05)/(minOf(a,b)+0.05)
        assertTrue("$key: contraste $ratio",ratio>=4.5)
        val row=JSONObject().put("pair",key).put("foreground",String.format("#%06X",foreground and 0xffffff))
            .put("background",String.format("#%06X",background and 0xffffff)).put("ratio",ratio)
            .put("foregroundPixels",foregroundPixels).put("backgroundPixels",backgroundPixels)
        File(context.cacheDir,"t070-contrast.jsonl").appendText(row.toString()+"\n")
    }
    private fun capture(name: String) {
        File(context.cacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun finalHeaderKeepsLongConfirmedClassStageLevelAndAllAttributesVisible() {
        mount { HomeScreen(ProgressView(progress(),loading=false),{},{}) }
        compose.onNodeWithTag("character-appearance").assertWidthIsEqualTo(88.dp).assertHeightIsEqualTo(88.dp)
            .assertContentDescriptionEquals("Guardián de la Sabiduría · Etapa III").assertIsDisplayed()
        compose.onNodeWithTag("character-class").assertTextEquals("Guardián de la Sabiduría").assertIsDisplayed()
        compose.onNodeWithTag("character-stage").assertTextEquals("Etapa III").assertIsDisplayed()
        compose.onNodeWithTag("character-level").assertTextEquals("Nivel general 35").assertIsDisplayed()
        for(id in listOf("STR","DEX","CON","INT","WIT","MEN")) compose.onNodeWithTag("attribute-$id-xp").assertIsDisplayed()
        compose.onNodeWithText("Clases").assertIsDisplayed()
        val image=compose.onNodeWithTag("character-appearance").fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithTag("character-class").fetchSemanticsNode().boundsInRoot
        assertTrue("La imagen no debe solapar el título",title.left>=image.right)
        capture("t070-home-final.png")
    }
    @Test fun homeTextAndEnabledControlsMeet45UsingActualRenderedColors() {
        mount { HomeScreen(ProgressView(progress(),loading=false),{},{}) }
        measure(compose.onNodeWithTag("character-class"),palette.text,palette.surface,"home-class")
        measure(compose.onNodeWithTag("character-stage"),palette.text,palette.surface,"home-stage")
        measure(compose.onNodeWithTag("character-level"),palette.text,palette.surface,"home-level")
        measure(compose.onNodeWithTag("attribute-STR-xp"),palette.text,palette.surface,"home-xp")
        measure(compose.onNodeWithText("Clases"),palette.buttonText,palette.button,"home-classes-button")
        measure(compose.onNodeWithText("Actualizar progreso"),palette.buttonText,palette.button,"home-refresh-button")
    }
    @Test fun disabledRefreshAndLoadingStaleMessagesKeepReadableConfirmedAppearance() {
        mount { HomeScreen(ProgressView(progress(),loading=true,stale=true),{},{}) }
        compose.onNodeWithText("Actualizar progreso").assertIsNotEnabled()
        measure(compose.onNodeWithText("Actualizar progreso"),palette.text,palette.card,"home-disabled-refresh")
        measure(compose.onNodeWithText("Actualizando progreso…"),palette.text,palette.surface,"home-loading")
        measure(compose.onNodeWithText("Mostrando el último progreso confirmado"),palette.text,palette.surface,"home-stale")
        compose.onNodeWithTag("character-appearance").assertIsDisplayed()
        capture("t070-home-loading.png")
    }
    @Test fun catalogueLockedAndUnlockedLabelsHaveContrastAndExplicitState() {
        mount { ClassCatalogScreen(ClassCatalogView(entries(),loading=false),14,false,{},{}) }
        compose.onNodeWithTag("class-catalog").performScrollToIndex(2)
        compose.onNodeWithTag("class-guerrero-stage-2-status").assertTextEquals("Bloqueada")
        measure(compose.onNodeWithTag("class-guerrero-stage-2-status"),palette.text,palette.card,"catalogue-locked")
        measure(compose.onNodeWithTag("class-guerrero-stage-2-minimum"),palette.text,palette.card,"catalogue-minimum")
        compose.onNodeWithTag("class-catalog").performScrollToIndex(0)
        compose.onNodeWithTag("class-novato-stage-1-status").assertTextEquals("Desbloqueada")
        measure(compose.onNodeWithTag("class-novato-stage-1-status"),palette.text,palette.card,"catalogue-unlocked")
        measure(compose.onNodeWithText("Volver a principal"),palette.buttonText,palette.button,"catalogue-back-button")
        capture("t070-catalogue-final.png")
    }
    @Test fun catalogueErrorAndStaleInformationRemainReadableAndRetryable() {
        mount { ClassCatalogScreen(ClassCatalogView(entries(),loading=false,error=ApiFailureCode.NETWORK_ERROR,stale=true),14,true,{},{}) }
        measure(compose.onNodeWithText("Usando el último nivel confirmado"),palette.text,palette.surface,"catalogue-stale-level")
        measure(compose.onNodeWithText("Mostrando el último catálogo confirmado"),palette.text,palette.surface,"catalogue-stale")
        measure(compose.onNodeWithText("No pudimos actualizar las clases"),palette.text,palette.surface,"catalogue-error")
        measure(compose.onNodeWithText("Reintentar clases"),palette.buttonText,palette.button,"catalogue-retry-button")
        capture("t070-catalogue-stale.png")
    }
}
