package lvluplife

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class VariantSecurityTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private fun xml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(file)

    private fun manifest(variant: String): Element = xml(File(
        "build/intermediates/merged_manifests/$variant/process${variant.replaceFirstChar { it.uppercase() }}Manifest/AndroidManifest.xml"
    )).getElementsByTagName("application").item(0) as Element

    private fun resource(variant: String, name: String) = xml(File(
        "build/intermediates/packaged_res/$variant/package${variant.replaceFirstChar { it.uppercase() }}Resources/xml/$name.xml"
    ))

    @Test fun releaseRejectsCleartextAndEmulatorOverrides() {
        val app = manifest("release")
        assertFalse(permissions(app).contains("android.permission.ACCESS_LOCAL_NETWORK"))
        assertEquals("false", app.getAttributeNS(androidNamespace, "usesCleartextTraffic"))
        assertEquals("@xml/network_security_config", app.getAttributeNS(androidNamespace, "networkSecurityConfig"))
        val config = resource("release", "network_security_config")
        val base = config.getElementsByTagName("base-config").item(0) as Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        assertEquals(0, config.getElementsByTagName("domain-config").length)
        assertFalse(config.documentElement.textContent.contains("10.0.2.2"))
    }

    @Test fun debugAllowsOnlyLocalCleartextExceptions() {
        assertEquals("true", manifest("debug").getAttributeNS(androidNamespace, "usesCleartextTraffic"))
        assertTrue(permissions(manifest("debug")).contains("android.permission.ACCESS_LOCAL_NETWORK"))
        val config = resource("debug", "network_security_config")
        val base = config.getElementsByTagName("base-config").item(0) as Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val domains = config.getElementsByTagName("domain")
        assertEquals(setOf("localhost", "127.0.0.1", "10.0.2.2"),
            (0 until domains.length).map { domains.item(it).textContent.trim() }.toSet())
        val local = config.getElementsByTagName("domain-config").item(0) as Element
        assertEquals("true", local.getAttribute("cleartextTrafficPermitted"))
    }

    private fun permissions(app: Element): Set<String> {
        val nodes = app.ownerDocument.getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNamespace, "name") }.toSet()
    }

    @Test fun bothVariantsExcludeDatabaseAndSidecarsFromAllBackupModes() {
        for (variant in listOf("debug", "release")) {
            val app = manifest(variant)
            assertEquals("false", app.getAttributeNS(androidNamespace, "allowBackup"))
            assertEquals("@xml/backup_rules", app.getAttributeNS(androidNamespace, "fullBackupContent"))
            assertEquals("@xml/data_extraction_rules", app.getAttributeNS(androidNamespace, "dataExtractionRules"))
            val legacy = resource(variant, "backup_rules").getElementsByTagName("exclude").item(0) as Element
            assertEquals("database", legacy.getAttribute("domain"))
            assertEquals(".", legacy.getAttribute("path"))
            val modern = resource(variant, "data_extraction_rules")
            for (mode in listOf("cloud-backup", "device-transfer")) {
                val section = modern.getElementsByTagName(mode).item(0) as Element
                val exclusion = section.getElementsByTagName("exclude").item(0) as Element
                assertEquals("database", exclusion.getAttribute("domain"))
                assertEquals(".", exclusion.getAttribute("path"))
            }
        }
    }
}
