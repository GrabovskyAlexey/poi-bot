package ru.grabovsky.poibot.config

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Защищает от опечаток в локализации: пропавшие ключи, расхождение ru/en, склеенные строки. */
class MessagesConsistencyTest : ShouldSpec({
    fun load(name: String): Properties = Properties().apply {
        MessagesConsistencyTest::class.java.getResourceAsStream("/$name")!!.reader(StandardCharsets.UTF_8).use { load(it) }
    }

    val ru = load("messages_ru.properties")
    val en = load("messages_en.properties")

    should("have identical key sets in ru and en") {
        (ru.stringPropertyNames() - en.stringPropertyNames()).shouldBeEmpty()
        (en.stringPropertyNames() - ru.stringPropertyNames()).shouldBeEmpty()
    }

    should("not contain several keys glued into one value") {
        val glued = listOf(ru, en).flatMap { props ->
            props.stringPropertyNames().filter { Regex("(buttons|alerts|unit)\\.[a-z_.]+=").containsMatchIn(props.getProperty(it)) }
        }
        glued.shouldBeEmpty()
    }

    should("define every message key used in the source code") {
        val keyPattern = Regex("\"((?:buttons|alerts|unit)\\.[a-z_]+(?:\\.[a-z_]+)*)\"")
        val sources = Path.of("src/main/kotlin")
        val used = Files.walk(sources).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.toList()
        }.flatMap { file -> keyPattern.findAll(Files.readString(file)).map { it.groupValues[1] }.toList() }.toSet()

        (used - ru.stringPropertyNames()).shouldBeEmpty()
    }

    should("define keys that are built dynamically for form fields") {
        listOf("name", "address", "location", "photo", "website", "description").forEach { field ->
            (ru.getProperty("buttons.add.field.$field") != null) shouldBe true
        }
    }
})
