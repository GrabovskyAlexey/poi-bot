package ru.grabovsky.poibot.service


import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import ru.grabovsky.poibot.entity.Poi
import ru.grabovsky.poibot.entity.StateEntity
import ru.grabovsky.poibot.mapper.PoiMapper
import ru.grabovsky.poibot.repository.VerificationRequestRepository
import ru.grabovsky.poibot.service.interfaces.StateService
import ru.grabovsky.poibot.service.interfaces.VerificationService
import ru.grabovsky.poibot.strategy.state.StateCode
import ru.grabovsky.poibot.strategy.state.StateCode.*

@Service
class VerificationServiceImpl(
    private val stateService: StateService,
    private val verificationRequestRepository: VerificationRequestRepository,
) : VerificationService {
    override fun verify(user: User, chat: Chat, stateCode: StateCode) {
        val verificationResult = runCatching {
            val request = stateService.getState(user, chat).verification ?:return
            request.result = when (request.stateCode) {
                ADD_DESC, ADD_NAME, ADD_ADDRESS -> request.message.text?.isNotEmpty() ?: false
                ADD_GEO -> request.message.location != null
                ADD_PHOTO -> request.message.photo?.isNotEmpty() ?: false
                else -> false
            }
            verificationRequestRepository.save(request)
            return@runCatching request.result
        }.onFailure { error ->
            logger.warn { "Failed to verify data. Error ${error.message}" }
        }.getOrDefault(false)

        if (verificationResult) {
            val state = stateService.getState(user, chat)
            val poi = state.poiData ?: Poi()
            val verification = requireNotNull(state.verification) {"Verification must not be null"}
            val message = verification.message
            when(verification.stateCode) {
                ADD_DESC -> poi.description = message.text
                ADD_NAME -> poi.name = message.text
                ADD_ADDRESS -> poi.address = message.text
                ADD_GEO -> poi.location = PoiMapper.mapLocationToPoint(message.location)
                else -> {}
            }
            state.poiData = poi
            stateService.saveState(state)
        }

    }

    @Transactional
    override fun clearVerifyRequest(state: StateEntity) {
        state.verification?.let { verificationRequestRepository.delete(it) }
        state
            .apply { this.verification = null }
            .also { stateService.saveState(it) }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}