package ru.grabovsky.poibot.mapper

import org.telegram.telegrambots.meta.api.objects.Location
import ru.grabovsky.poibot.entity.PoIEntity
import ru.grabovsky.poibot.entity.Poi
import ru.grabovsky.poibot.entity.Point

object PoiMapper {
    fun mapToPoiEntity(poi: Poi, userId: Long, chatId: Long) = PoIEntity(
        chatId = chatId,
        userId = userId,
        name = poi.name,
        address = poi.address,
        description = poi.description,
        location = poi.location
    )

    fun mapLocationToPoint(location: Location?): Point? {
        if (location == null) {
            return null
        }
        return Point(location.latitude.toBigDecimal(), location.longitude.toBigDecimal())
    }
}