package ru.grabovsky.poibot.service.interfaces

import ru.grabovsky.poibot.entity.PoIEntity

interface PoiService {
    fun savePoi(entity: PoIEntity)
}