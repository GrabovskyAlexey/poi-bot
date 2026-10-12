package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery

/** Inline-режим `@бот текст`: пользователь выбирает своё место и отправляет его карточку в любой чат. */
interface InlineShareService {
    fun answer(query: InlineQuery)

    companion object {
        /** Сколько мест показывать в ответе (лимит Telegram - 50). */
        const val MAX_RESULTS = 20
    }
}
