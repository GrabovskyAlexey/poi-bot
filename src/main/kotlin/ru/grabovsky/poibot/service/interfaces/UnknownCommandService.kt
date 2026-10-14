package ru.grabovsky.poibot.service.interfaces

import org.telegram.telegrambots.meta.api.objects.message.Message

/** Ответ на команду, которой нет (или которая недоступна пользователю): неотличим для обоих случаев. */
interface UnknownCommandService {
    /** В личке отвечает «Неизвестная команда» и, если у пользователя включено автоудаление, через пару секунд удаляет команду и ответ; в группах молчит. */
    fun reply(message: Message)

    companion object {
        const val DELETE_AFTER_SECONDS = 5L
    }
}
