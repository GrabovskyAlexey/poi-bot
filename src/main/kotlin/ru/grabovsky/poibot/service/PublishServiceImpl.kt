package ru.grabovsky.poibot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.grabovsky.poibot.entity.Chat
import ru.grabovsky.poibot.entity.SavedPlace
import ru.grabovsky.poibot.entity.SavedPlaceChat
import ru.grabovsky.poibot.entity.SavedPlaceChatId
import ru.grabovsky.poibot.entity.UserChatId
import ru.grabovsky.poibot.repository.ChatRepository
import ru.grabovsky.poibot.repository.SavedPlaceChatRepository
import ru.grabovsky.poibot.repository.SavedPlaceRepository
import ru.grabovsky.poibot.repository.UserChatRepository
import ru.grabovsky.poibot.service.interfaces.*

@Service
class PublishServiceImpl(
    private val chatService: ChatService,
    private val chatRepository: ChatRepository,
    private val userChatRepository: UserChatRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val savedPlaceChatRepository: SavedPlaceChatRepository,
    private val membershipChecker: ChatMembershipChecker,
) : PublishService {

    override fun getUserGroups(userId: Long): List<Chat> =
        chatService.getUserChats(userId).filter { chat ->
            val member = membershipChecker.isMember(chat.id, userId)
            if (member == false) {
                chatService.unlinkUser(userId, chat.id)
            }
            member != false
        }

    @Transactional(readOnly = true)
    override fun publishedCounts(placeIds: Collection<Long>, chatIds: Collection<Long>): Map<Long, Int> {
        if (placeIds.isEmpty() || chatIds.isEmpty()) return emptyMap()
        return savedPlaceChatRepository.countByChats(placeIds, chatIds).associate { it.chatId to it.total.toInt() }
    }

    @Transactional
    override fun setPublished(userId: Long, placeIds: Collection<Long>, chatId: Long, publish: Boolean): Int {
        val chat = chatRepository.findById(chatId).orElse(null)
        if (chat == null || !chat.isActive || !userChatRepository.existsById(UserChatId(userId, chatId))) {
            throw PublishNotAllowedException("User $userId is not linked to active chat $chatId")
        }
        val owned = savedPlaceRepository.findByIdInAndOwnerId(placeIds, userId)
        var changed = 0
        owned.forEach { place ->
            val id = SavedPlaceChatId(place.id!!, chatId)
            val exists = savedPlaceChatRepository.existsById(id)
            when {
                publish && !exists -> {
                    savedPlaceChatRepository.save(SavedPlaceChat(id, sharedBy = userId))
                    changed++
                }

                !publish && exists -> {
                    savedPlaceChatRepository.deleteById(id)
                    changed++
                }
            }
        }
        return changed
    }

    @Transactional
    override fun unpublishAsModerator(userId: Long, chatId: Long, savedPlaceId: Long): Boolean {
        val place = savedPlaceRepository.findPublishedInChatById(chatId, savedPlaceId) ?: return false
        if (!canModerate(userId, chatId, place)) return false
        savedPlaceChatRepository.deleteById(SavedPlaceChatId(savedPlaceId, chatId))
        return true
    }

    override fun canModerate(userId: Long, chatId: Long, place: SavedPlace): Boolean =
        place.ownerId == userId || membershipChecker.isAdmin(chatId, userId)

    @Transactional(readOnly = true)
    override fun listPublished(chatId: Long, page: Int, pageSize: Int): SavedPlacePage {
        var result = savedPlaceRepository.findPublishedInChat(chatId, PageRequest.of(page.coerceAtLeast(0), pageSize))
        val totalPages = result.totalPages.coerceAtLeast(1)
        if (page >= totalPages) {
            result = savedPlaceRepository.findPublishedInChat(chatId, PageRequest.of(totalPages - 1, pageSize))
        }
        return SavedPlacePage(result.content, result.number, totalPages, result.totalElements)
    }

    @Transactional(readOnly = true)
    override fun getPublished(chatId: Long, savedPlaceId: Long): SavedPlace? =
        savedPlaceRepository.findPublishedInChatById(chatId, savedPlaceId)
}
