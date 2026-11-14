package lvluplife.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.pow
import lvluplife.MainActivity
import lvluplife.application.*
import lvluplife.data.ApiFailureCode
import lvluplife.ui.theme.AttributeColors
import lvluplife.ui.theme.LvlUpFonts
import lvluplife.ui.theme.LvlUpTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/**
 * «Arcade vivo» palette (feature 002, FR-001 to FR-004). Expected colors are the spec values, not read
 * from the theme; WCAG sRGB pairs are verified in actual glyph/background pixels.
 */
class AppearanceContrastTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private object Spec {
        val background=0xFFEAF1FC.toInt(); val card=0xFFFFFFFF.toInt(); val text=0xFF14213D.toInt()
        val muted=0xFF5A6785.toInt(); val primary=0xFF2F5BD3.toInt(); val onPrimary=0xFFFFFFFF.toInt()
        val accent=0xFFFFC531.toInt(); val track=0xFFD6E0F3.toInt()
        val fill=mapOf("STR" to 0xFFE5484D,"DEX" to 0xFF12A594,"CON" to 0xFFE38B0B,"INT" to 0xFF7C5CE0,
            "WIT" to 0xFF2F7DE1,"MEN" to 0xFFD6409F).mapValues { it.value.toInt() }
        val attributeText=mapOf("STR" to 0xFFC62A2F,"DEX" to 0xFF0B7F72,"CON" to 0xFFA85E00,"INT" to 0xFF6346C8,
            "WIT" to 0xFF1F63BE,"MEN" to 0xFFB02A80).mapValues { it.value.toInt() }
    }
    private val ids=listOf("STR","DEX","CON","INT","WIT","MEN")
    companion object {
        @JvmStatic @BeforeClass fun clearReport() {
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"t004-contrast.jsonl").delete()
        }
    }
    private fun mount(night: Boolean=false,content: @Composable () -> Unit) {
        compose.runOnIdle { compose.activity.setContent { if(night) NightSystem { LvlUpTheme(content) } else LvlUpTheme(content) } }
        compose.waitForIdle()
    }
    private fun progress()=RecordProgressView(12,"synthetic-final-cycle","2026-11-12",35,"guardian-de-la-sabiduria",3,
        ids.associateWith { RecordAttributeView(15,7,450) },"Guardián de la Sabiduría")
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
    private fun ratio(foreground: Int,background: Int): Double {
        val a=luminance(foreground);val b=luminance(background)
        return (maxOf(a,b)+0.05)/(minOf(a,b)+0.05)
    }
    private fun report(key: String,foreground: Int,background: Int,value: Double,extra: JSONObject=JSONObject()) {
        val row=extra.put("pair",key).put("foreground",String.format("#%06X",foreground and 0xffffff))
            .put("background",String.format("#%06X",background and 0xffffff)).put("ratio",value)
        File(context.cacheDir,"t004-contrast.jsonl").appendText(row.toString()+"\n")
    }
    private fun measure(node: SemanticsNodeInteraction,foreground: Int,background: Int,key: String) {
        val bitmap=node.captureToImage().asAndroidBitmap()
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val foregroundPixels=pixels.count { it==foreground }
        val backgroundPixels=pixels.count { it==background }
        assertTrue("$key: color de texto no observado",foregroundPixels>0)
        assertTrue("$key: fondo esperado no observado",backgroundPixels>0)
        val value=ratio(foreground,background)
        assertTrue("$key: contraste $value",value>=4.5)
        report(key,foreground,background,value,JSONObject().put("foregroundPixels",foregroundPixels).put("backgroundPixels",backgroundPixels))
    }
    private fun capture(name: String) {
        File(context.cacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun themeExposesTheArcadePaletteFontsAndRoundedShapes() {
        lateinit var colors: ColorScheme; lateinit var type: Typography; lateinit var shapes: Shapes; var cardColor=0
        mount {
            val c=MaterialTheme.colorScheme;val t=MaterialTheme.typography;val s=MaterialTheme.shapes
            val card=CardDefaults.cardColors().containerColor.toArgb()
            SideEffect { colors=c;type=t;shapes=s;cardColor=card }
        }
        compose.runOnIdle {
            assertEquals(Spec.background,colors.background.toArgb());assertEquals(Spec.background,colors.surface.toArgb())
            assertEquals(Spec.text,colors.onBackground.toArgb());assertEquals(Spec.text,colors.onSurface.toArgb())
            assertEquals(Spec.muted,colors.onSurfaceVariant.toArgb())
            assertEquals(Spec.primary,colors.primary.toArgb());assertEquals(Spec.onPrimary,colors.onPrimary.toArgb())
            assertEquals(Spec.accent,colors.secondaryContainer.toArgb());assertEquals(Spec.text,colors.onSecondaryContainer.toArgb())
            assertEquals(Spec.track,colors.surfaceVariant.toArgb())
            assertEquals(Spec.card,cardColor);assertEquals(Spec.card,colors.surfaceContainerHighest.toArgb())
            for(id in ids) {
                assertEquals(id,Spec.fill.getValue(id),AttributeColors.of(id).fill.toArgb())
                assertEquals(id,Spec.attributeText.getValue(id),AttributeColors.of(id).text.toArgb())
            }
            for(style in listOf(type.displayLarge,type.headlineMedium,type.headlineSmall,type.titleLarge,type.titleMedium,type.labelLarge))
                assertSame(LvlUpFonts.display,style.fontFamily)
            for(style in listOf(type.bodyLarge,type.bodyMedium,type.bodySmall)) assertSame(LvlUpFonts.text,style.fontFamily)
            assertEquals(RoundedCornerShape(14.dp),shapes.small)
            assertEquals(RoundedCornerShape(16.dp),shapes.medium)
        }
    }
    @Test fun everyPaletteTextAndControlPairMeets45() {
        val pairs=mutableListOf(
            Triple("text/background",Spec.text,Spec.background),Triple("text/card",Spec.text,Spec.card),
            Triple("muted/background",Spec.muted,Spec.background),Triple("muted/card",Spec.muted,Spec.card),
            Triple("primary-button",Spec.onPrimary,Spec.primary),Triple("accent-button",Spec.text,Spec.accent),
            Triple("disabled-button",Spec.text,Spec.card))
        for(id in ids) pairs+=Triple("attribute-$id/card",Spec.attributeText.getValue(id),Spec.card)
        for((key,foreground,background) in pairs) {
            val value=ratio(foreground,background)
            report(key,foreground,background,value)
            assertTrue("$key: contraste $value",value>=4.5)
        }
    }
    @Test fun attributeTextColorsRenderReadablyOnCardsAndKeepTheirWrittenCode() {
        mount { Card(Modifier.padding(16.dp)) { Column(Modifier.padding(16.dp)) {
            for(id in ids) Text("$id · Nivel 7",Modifier.testTag("sample-$id"),color=AttributeColors.of(id).text)
        } } }
        for(id in ids) {
            compose.onNodeWithTag("sample-$id").assertTextEquals("$id · Nivel 7")
            measure(compose.onNodeWithTag("sample-$id"),Spec.attributeText.getValue(id),Spec.card,"rendered-attribute-$id")
        }
        capture("t004-attribute-colors.png")
    }
    @Test fun systemDarkModeStillShowsTheLightArcadeScheme() {
        var systemDark=false; var surface=0
        mount(night=true) {
            val dark=isSystemInDarkTheme();val color=MaterialTheme.colorScheme.surface.toArgb()
            SideEffect { systemDark=dark;surface=color }
            HomeScreen(ProgressView(progress(),loading=false),{},{})
        }
        compose.runOnIdle { assertTrue("El sistema simulado debe estar en modo oscuro",systemDark) }
        assertEquals(Spec.background,surface)
        measure(compose.onNodeWithTag("character-class"),Spec.text,Spec.background,"dark-system-home-class")
        measure(compose.onNodeWithText("Actualizar progreso"),Spec.onPrimary,Spec.primary,"dark-system-refresh-button")
        capture("t004-dark-system-home.png")
    }
    @Test fun finalHeaderKeepsLongConfirmedClassStageLevelAndAllAttributesVisible() {
        mount { HomeScreen(ProgressView(progress(),loading=false),{},{}) }
        compose.onNodeWithTag("character-appearance").assertWidthIsEqualTo(88.dp).assertHeightIsEqualTo(88.dp)
            .assertContentDescriptionEquals("Guardián de la Sabiduría · Etapa III").assertIsDisplayed()
        compose.onNodeWithTag("character-class").assertTextEquals("Guardián de la Sabiduría").assertIsDisplayed()
        compose.onNodeWithTag("character-stage").assertTextEquals("Etapa III").assertIsDisplayed()
        compose.onNodeWithTag("character-level").assertTextEquals("Nivel general 35").assertIsDisplayed()
        for(id in ids) compose.onNodeWithTag("attribute-$id-xp").assertIsDisplayed()
        compose.onNodeWithText("Clases").assertIsDisplayed()
        val image=compose.onNodeWithTag("character-appearance").fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithTag("character-class").fetchSemanticsNode().boundsInRoot
        assertTrue("La imagen no debe solapar el título",title.left>=image.right)
        capture("t004-home.png")
    }
    @Test fun homeTextAndEnabledControlsMeet45UsingActualRenderedColors() {
        mount { HomeScreen(ProgressView(progress(),loading=false),{},{}) }
        measure(compose.onNodeWithTag("character-class"),Spec.text,Spec.background,"home-class")
        measure(compose.onNodeWithTag("character-stage"),Spec.text,Spec.background,"home-stage")
        measure(compose.onNodeWithTag("character-level"),Spec.text,Spec.background,"home-level")
        measure(compose.onNodeWithTag("attribute-STR-xp"),Spec.text,Spec.background,"home-xp")
        measure(compose.onNodeWithText("Clases"),Spec.onPrimary,Spec.primary,"home-classes-button")
        measure(compose.onNodeWithText("Actualizar progreso"),Spec.onPrimary,Spec.primary,"home-refresh-button")
    }
    @Test fun disabledRefreshAndLoadingStaleMessagesKeepReadableConfirmedAppearance() {
        mount { HomeScreen(ProgressView(progress(),loading=true,stale=true),{},{}) }
        compose.onNodeWithText("Actualizar progreso").assertIsNotEnabled()
        measure(compose.onNodeWithText("Actualizar progreso"),Spec.text,Spec.card,"home-disabled-refresh")
        measure(compose.onNodeWithText("Actualizando progreso…"),Spec.text,Spec.background,"home-loading")
        measure(compose.onNodeWithText("Mostrando el último progreso confirmado"),Spec.text,Spec.background,"home-stale")
        compose.onNodeWithTag("character-appearance").assertIsDisplayed()
        capture("t004-home-loading.png")
    }
    @Test fun catalogueLockedAndUnlockedLabelsHaveContrastAndExplicitState() {
        mount { ClassCatalogScreen(ClassCatalogView(entries(),loading=false),14,false,{},{}) }
        compose.onNodeWithTag("class-catalog").performScrollToIndex(2)
        compose.onNodeWithTag("class-guerrero-stage-2-status").assertTextEquals("Bloqueada")
        measure(compose.onNodeWithTag("class-guerrero-stage-2-status"),Spec.text,Spec.card,"catalogue-locked")
        measure(compose.onNodeWithTag("class-guerrero-stage-2-minimum"),Spec.text,Spec.card,"catalogue-minimum")
        compose.onNodeWithTag("class-catalog").performScrollToIndex(0)
        compose.onNodeWithTag("class-novato-stage-1-status").assertTextEquals("Desbloqueada")
        measure(compose.onNodeWithTag("class-novato-stage-1-status"),Spec.text,Spec.card,"catalogue-unlocked")
        measure(compose.onNodeWithText("Volver a principal"),Spec.onPrimary,Spec.primary,"catalogue-back-button")
        capture("t004-catalogue.png")
    }
    @Test fun catalogueErrorAndStaleInformationRemainReadableAndRetryable() {
        mount { ClassCatalogScreen(ClassCatalogView(entries(),loading=false,error=ApiFailureCode.NETWORK_ERROR,stale=true),14,true,{},{}) }
        measure(compose.onNodeWithText("Usando el último nivel confirmado"),Spec.text,Spec.background,"catalogue-stale-level")
        measure(compose.onNodeWithText("Mostrando el último catálogo confirmado"),Spec.text,Spec.background,"catalogue-stale")
        measure(compose.onNodeWithText("No pudimos actualizar las clases"),Spec.text,Spec.background,"catalogue-error")
        measure(compose.onNodeWithText("Reintentar clases"),Spec.onPrimary,Spec.primary,"catalogue-retry-button")
        capture("t004-catalogue-stale.png")
    }
}
