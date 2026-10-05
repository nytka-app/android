package io.github.nytka_app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every translatable resource of `values/` has a twin in `values-uk/`, and the other way round. */
class StringsParityTest {
    private val res = File("src/main/res")

    private fun names(dir: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val nodes = doc.documentElement.childNodes
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeName in setOf("string", "plurals", "string-array") }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .map { it.nodeName + " " + it.attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    @Test
    fun `English and Ukrainian define the same strings, plurals and arrays`() {
        val en = names("values")
        val uk = names("values-uk")

        assertEquals("only in values-uk", emptySet<String>(), uk - en)
        assertEquals("only in values", emptySet<String>(), en - uk)
    }
}
