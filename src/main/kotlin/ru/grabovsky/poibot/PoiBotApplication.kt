package ru.grabovsky.poibot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PoiBotApplication

fun main(args: Array<String>) {
    runApplication<PoiBotApplication>(*args)
}
