package io.github.sonnayasomnambula.mezzoparser

import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter

fun XmlSerializer.document(docName: String = "UTF-8",
                           docType: String = "  tv SYSTEM \"xmltv.dtd\"",
                           xmlStringWriter: StringWriter = StringWriter(),
                           init: XmlSerializer.() -> Unit): String {
    startDocument(docName, false)
    docdecl(docType)
    xmlStringWriter.buffer.setLength(0) //  refreshing string writer due to reuse
    setOutput(xmlStringWriter)
    init()
    endDocument()
    return xmlStringWriter.toString()
}

//  element
fun XmlSerializer.element(name: String, init: XmlSerializer.() -> Unit) {
    startTag("", name)
    init()
    endTag("", name)
}

//  element with attribute & content
fun XmlSerializer.element(name: String,
                          content: String,
                          init: XmlSerializer.() -> Unit) {
    startTag("", name)
    init()
    text(content)
    endTag("", name)
}

//  element with content
fun XmlSerializer.element(name: String, content: String) =
    element(name) {
        text(content)
    }

//  attribute
fun XmlSerializer.attribute(name: String, value: String) =
    attribute("", name, value)
