package ru.grabovsky.poibot.service

import org.springframework.stereotype.Service
import ru.grabovsky.poibot.entity.PoIEntity
import ru.grabovsky.poibot.repository.PoiRepository
import ru.grabovsky.poibot.service.interfaces.PoiService

@Service
class PoiServiceImpl(
    private val poiRepository: PoiRepository
) : PoiService {

    override fun savePoi(entity: PoIEntity) {
        poiRepository.saveAndFlush(entity)
    }

}