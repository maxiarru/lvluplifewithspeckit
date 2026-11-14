package lvluplife.ui

import android.content.Context
import android.graphics.BitmapFactory
import org.junit.Assert.*

/** Feature 002: class images are transparent cut-outs of the main figure, never larger than the 1122×1402 originals. */
object ClassCutoutAssertions {
    fun assertTransparentCutout(context: Context, drawable: Int, name: String) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.resources.openRawResource(drawable).use { BitmapFactory.decodeStream(it, null, bounds) }
        assertEquals("$name: formato", "image/png", bounds.outMimeType)
        assertTrue("$name: ancho ${bounds.outWidth}", bounds.outWidth in 200..1122)
        assertTrue("$name: alto ${bounds.outHeight}", bounds.outHeight in 300..1402)
        val sampled = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = context.resources.openRawResource(drawable).use { BitmapFactory.decodeStream(it, null, sampled) }!!
        try {
            assertTrue("$name: sin canal alfa", bitmap.hasAlpha())
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val transparent = pixels.count { (it ushr 24) == 0 }.toDouble() / pixels.size
            val opaque = pixels.count { (it ushr 24) == 255 }.toDouble() / pixels.size
            assertTrue("$name: fondo transparente $transparent", transparent >= 0.10)
            assertTrue("$name: figura opaca $opaque", opaque >= 0.20)
        } finally { bitmap.recycle() }
    }
}
