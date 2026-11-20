package com.forgeprom.studio.core.util

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipInputStream

/** Reads EPUB spine documents locally, without unpacking files or resolving external resources. */
internal object EpubBook {
    private const val MIB = 1024 * 1024

    fun read(input: InputStream): List<String> {
        val bounded = object : FilterInputStream(input) {
            private var bytes = 0L
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) count(it) }
            override fun read(): Int = super.read().also { if (it >= 0) count(1) }
            private fun count(n: Int) { bytes += n; require(bytes <= 16 * MIB) }
        }
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bounded).use { zip ->
            val buffer = ByteArray(8192)
            var total = 0
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 2048)
                require(entry.name != "META-INF/encryption.xml")
                val keep = !entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in
                    setOf("xml", "opf", "html", "xhtml", "htm")
                val output = if (keep) ByteArrayOutputStream() else null
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 32 * MIB)
                    if (output != null) {
                        require(output.size() + n <= 4 * MIB)
                        output.write(buffer, 0, n)
                    }
                }
                if (output != null) require(entries.put(entry.name, output.toByteArray()) == null)
            }
        }
        var root: String? = null
        tags(entries.getValue("META-INF/container.xml")) { parser ->
            if (parser.name == "rootfile" && root == null) root = parser.attr("full-path")
        }
        val opf = requireNotNull(root)
        val manifest = mutableMapOf<String, Pair<String, String>>()
        val spine = mutableListOf<String>()
        tags(entries.getValue(opf)) { parser ->
            when (parser.name) {
                "item" -> if (parser.depth == 3) {
                    manifest[parser.attr("id")] = parser.attr("href") to parser.attr("media-type")
                }
                "itemref" -> if (parser.depth == 3) spine.add(parser.attr("idref"))
            }
        }
        require(spine.isNotEmpty() && spine.size <= 2048)
        val base = URI(null, null, "/$opf", null)
        val text = buildString {
            for (id in spine) {
                val (href, type) = manifest.getValue(id)
                require(type == "application/xhtml+xml" || type == "text/html")
                val resolved = base.resolve(href).normalize()
                require(!resolved.isAbsolute && resolved.rawAuthority == null && resolved.rawQuery == null)
                val path = resolved.path.removePrefix("/")
                require(path.split('/').none { it == ".." })
                val source = entries.getValue(path).toString(Charsets.UTF_8)
                val body = Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                if (body.isNotEmpty()) {
                    require(length + body.length + 2 <= 10_000_002)
                    if (isNotEmpty()) append("\n\n")
                    append(body)
                }
            }
        }
        return StoryBookParts.split(text)
    }

    private fun XmlPullParser.attr(name: String): String = requireNotNull(getAttributeValue(null, name))

    private fun tags(bytes: ByteArray, visit: (XmlPullParser) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(bytes.inputStream(), null)
        while (true) when (parser.nextToken()) {
            XmlPullParser.END_DOCUMENT -> return
            XmlPullParser.DOCDECL -> error("Unexpected document declaration")
            XmlPullParser.START_TAG -> visit(parser)
        }
    }
}
