package com.mashup.dojo.service

import com.mashup.dojo.DojoException
import com.mashup.dojo.DojoExceptionType
import com.mashup.dojo.PickEntity
import com.mashup.dojo.PickRepository
import com.mashup.dojo.PickTimeRepository
import com.mashup.dojo.domain.Member
import com.mashup.dojo.domain.MemberGender
import com.mashup.dojo.domain.MemberId
import com.mashup.dojo.domain.MemberPlatform
import com.mashup.dojo.domain.Pick
import com.mashup.dojo.domain.PickId
import com.mashup.dojo.domain.PickOpenItem
import com.mashup.dojo.domain.PickSort
import com.mashup.dojo.domain.QuestionId
import com.mashup.dojo.domain.QuestionSetId
import com.mashup.dojo.domain.QuestionSheetId
import com.mashup.dojo.service.PickService.PickOpenInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

interface PickService {
    fun getReceivedPickPaging(
        pickedMemberId: MemberId,
        sort: PickSort,
        pageNumber: Int,
        pageSize: Int,
    ): GetPickPaging

    fun getSolvedPickList(
        pickerMemberId: MemberId,
        questionSetId: QuestionSetId,
    ): List<Pick>

    fun create(
        questionId: QuestionId,
        questionSetId: QuestionSetId,
        questionSheetId: QuestionSheetId,
        pickerMemberId: MemberId,
        pickedMemberId: MemberId,
    ): PickId

    fun openPick(
        pickId: PickId,
        pickedId: MemberId,
        pickOpenItem: PickOpenItem,
    ): PickOpenInfo

    fun getPickDetailPaging(
        questionId: QuestionId,
        memberId: MemberId,
        pageNumber: Int,
        pageSize: Int,
    ): GetPickDetailPaging

    fun getPickCount(
        questionId: QuestionId,
        memberId: MemberId,
    ): Int

    // 픽 당한 횟수 조회
    fun findPickedCountByMemberId(memberId: MemberId): Int

    fun getNextPickTime(): LocalDateTime

    fun getReceivedSpacePicks(memberId: MemberId): List<SpacePickDetail>

    data class GetPickPaging(
        val picks: List<GetReceivedPick>,
        val totalPage: Int,
        val totalElements: Long,
        val isFirst: Boolean,
        val isLast: Boolean,
    )

    data class GetReceivedPick(
        val pickId: PickId,
        val questionId: QuestionId,
        val questionContent: String,
        val questionEmojiImageUrl: String,
        val latestPickedAt: LocalDateTime,
        val totalReceivedPickCount: Int,
    )

    data class GetPickDetailPaging(
        val picks: List<GetReceivedPickDetail>,
        val totalPage: Int,
        val totalElements: Long,
        val isFirst: Boolean,
        val isLast: Boolean,
    )

    data class GetReceivedPickDetail(
        val pickId: PickId,
        val pickerProfileImageUrl: String,
        val pickerOrdinal: Int,
        val pickerIdOpen: Boolean,
        val pickerId: MemberId,
        val pickerGenderOpen: Boolean,
        val pickerGender: MemberGender?,
        val pickerPlatformOpen: Boolean,
        val pickerPlatform: MemberPlatform?,
        val pickerSecondInitialNameOpen: Boolean,
        val pickerSecondInitialName: String?,
        val pickerFullNameOpen: Boolean,
        val pickerFullName: String?,
        val latestPickedAt: LocalDateTime,
    )

    data class SpacePickDetail(
        val pickId: PickId,
        val questionId: QuestionId,
        val rank: Int = -1,
        val pickContent: String,
        val pickCount: Int,
        val createdAt: LocalDateTime,
    )

    data class PickOpenInfo(
        val pickOpenValue: String,
        val pickOpenImageUrl: String,
    )
}

@Component
@ConfigurationProperties(prefix = "dojo.profile")
class ProfileImageProperties {
    lateinit var male: String
    lateinit var female: String
    lateinit var unknown: String
}

@Component
@ConfigurationProperties(prefix = "dojo.platform")
class PlatformImageProperties {
    lateinit var android: String
    lateinit var design: String
    lateinit var ios: String
    lateinit var node: String
    lateinit var spring: String
    lateinit var web: String
}

@Transactional(readOnly = true)
@Service
class DefaultPickService(
    private val pickRepository: PickRepository,
    private val memberService: MemberService,
    private val pickTimeRepository: PickTimeRepository,
    @Value("\${dojo.rank.size}")
    private val defaultRankSize: Long,
    private val profileImageProperties: ProfileImageProperties,
    private val platformImageProperties: PlatformImageProperties,
    private val imageService: ImageService,
) : PickService {
    override fun getReceivedPickPaging(
        pickedMemberId: MemberId,
        sort: PickSort,
        pageNumber: Int,
        pageSize: Int,
    ): PickService.GetPickPaging {
        val pageable = PageRequest.of(pageNumber, pageSize)

        val pickPaging =
            pickRepository.findGroupByPickPaging(
                pickedId = pickedMemberId.value,
                sort = sort.name,
                pageable = pageable
            )

        val receivedPicks =
            pickPaging.content.map {
                PickService.GetReceivedPick(
                    pickId = PickId(it.pickId),
                    questionId = QuestionId(it.questionId),
                    questionContent = it.questionContent,
                    questionEmojiImageUrl = it.questionEmojiImageUrl,
                    latestPickedAt = it.latestPickedAt,
                    totalReceivedPickCount = it.totalReceivedPickCount.toInt()
                )
            }

        return PickService.GetPickPaging(
            picks = receivedPicks,
            totalPage = pickPaging.totalPages,
            totalElements = pickPaging.totalElements,
            isFirst = pickPaging.isFirst,
            isLast = pickPaging.isLast
        )
    }

    override fun getSolvedPickList(
        pickerMemberId: MemberId,
        questionSetId: QuestionSetId,
    ): List<Pick> {
        return pickRepository.findSolvedPick(pickerMemberId.value, questionSetId.value)
            .map { it.toPick() }
    }

    @Transactional
    override fun create(
        questionId: QuestionId,
        questionSetId: QuestionSetId,
        questionSheetId: QuestionSheetId,
        pickerMemberId: MemberId,
        pickedMemberId: MemberId,
    ): PickId {
        val pick =
            Pick.create(
                questionId = questionId,
                questionSetId = questionSetId,
                questionSheetId = questionSheetId,
                pickerId = pickerMemberId,
                pickedId = pickedMemberId
            )

        val id: String = pickRepository.save(pick.toEntity()).id
        return PickId(id)
    }

    @Transactional
    override fun openPick(
        pickId: PickId,
        pickedId: MemberId,
        pickOpenItem: PickOpenItem,
    ): PickOpenInfo {
        val pick = findPickById(pickId) ?: throw DojoException.of(DojoExceptionType.PICK_NOT_FOUND)

        if (pick.pickedId != pickedId) {
            throw DojoException.of(DojoExceptionType.ACCESS_DENIED)
        }

        if (pick.isOpened(pickOpenItem)) {
            throw DojoException.of(DojoExceptionType.PICK_ALREADY_OPENED)
        }

        pickRepository.save(
            pick.updateOpenItem(pickOpenItem).toEntity()
        )

        val picker = memberService.findMemberById(pick.pickerId) ?: throw DojoException.of(DojoExceptionType.MEMBER_NOT_FOUND)

        return getOpenItem(pickOpenItem, picker)
    }

    private fun getOpenItem(
        pickOpenItem: PickOpenItem,
        picker: Member,
    ): PickOpenInfo {
        val pickOpenValue: String
        var pickOpenImageUrl = ""

        if (PickOpenItem.GENDER == pickOpenItem) {
            pickOpenValue = picker.gender.name
            pickOpenImageUrl =
                when (picker.gender) {
                    MemberGender.MALE -> profileImageProperties.male
                    MemberGender.FEMALE -> profileImageProperties.female
                    MemberGender.UNKNOWN -> profileImageProperties.unknown
                }
        } else if (PickOpenItem.PLATFORM == pickOpenItem) {
            pickOpenValue = picker.platform.name
            pickOpenImageUrl =
                when (picker.platform) {
                    MemberPlatform.ANDROID -> platformImageProperties.android
                    MemberPlatform.IOS -> platformImageProperties.ios
                    MemberPlatform.WEB -> platformImageProperties.web
                    MemberPlatform.NODE -> platformImageProperties.node
                    MemberPlatform.DESIGN -> platformImageProperties.design
                    MemberPlatform.SPRING -> platformImageProperties.spring
                    MemberPlatform.UNKNOWN -> ""
                }
        } else if (PickOpenItem.MID_INITIAL_NAME == pickOpenItem) {
            pickOpenValue = picker.secondInitialName
        } else {
            pickOpenValue = picker.fullName
            pickOpenImageUrl = imageService.load(picker.profileImageId)?.url ?: profileImageProperties.unknown
        }

        return PickOpenInfo(pickOpenValue, pickOpenImageUrl)
    }

    private fun findPickById(pickId: PickId): Pick? {
        return pickRepository.findByIdOrNull(pickId.value)?.toPick()
    }

    override fun getPickDetailPaging(
        questionId: QuestionId,
        memberId: MemberId,
        pageNumber: Int,
        pageSize: Int,
    ): PickService.GetPickDetailPaging {
        val pageable = PageRequest.of(pageNumber, pageSize)
        val pagingPick = pickRepository.findPickDetailPaging(memberId = memberId.value, questionId = questionId.value, pageable = pageable)

        val receivedPickDetails =
            pagingPick.content.map { pickEntity ->

                val genderOpen = pickEntity.isGenderOpen
                val platformOpen = pickEntity.isPlatformOpen
                val secondInitialNameOpen = pickEntity.isMidInitialNameOpen
                val fullNameOpen = pickEntity.isFullNameOpen
                val pickerIdOpen = fullNameOpen && genderOpen && platformOpen && secondInitialNameOpen

                val pickerId = transformPickerId(isOpen = pickerIdOpen, pickerId = MemberId(pickEntity.pickerId))
                val pickerGender = transformPickerGender(isOpen = genderOpen, pickerGender = MemberGender.findByValue(pickEntity.pickerGender))
                val pickerPlatform = transformPickerPlatform(isOpen = platformOpen, pickerPlatform = MemberPlatform.findByValue(pickEntity.pickerPlatform))
                val pickerSecondInitialName = transformPickerSecondInitialName(isOpen = secondInitialNameOpen, secondInitialName = pickEntity.pickerSecondInitialName)
                val pickerFullName = transformPickerFullName(isOpen = fullNameOpen, fullName = pickEntity.pickerFullName)

                val pickerProfileImageUrl = pickEntity.pickerProfileImageUrl

                val transformProfileImageUrl: String =
                    transformPickerProfileImageUrl(
                        pickerProfileImageUrl = pickerProfileImageUrl,
                        pickerGender = pickerGender,
                        pickerFullName = pickerFullName
                    )

                PickService.GetReceivedPickDetail(
                    pickId = PickId(pickEntity.pickId),
                    pickerProfileImageUrl = transformProfileImageUrl,
                    pickerOrdinal = pickEntity.pickerOrdinal,
                    pickerIdOpen = pickerIdOpen,
                    pickerId = pickerId,
                    pickerGenderOpen = genderOpen,
                    pickerGender = pickerGender,
                    pickerPlatformOpen = platformOpen,
                    pickerPlatform = pickerPlatform,
                    pickerSecondInitialNameOpen = secondInitialNameOpen,
                    pickerSecondInitialName = pickerSecondInitialName,
                    pickerFullNameOpen = fullNameOpen,
                    pickerFullName = pickerFullName,
                    latestPickedAt = pickEntity.createdAt
                )
            }

        return PickService.GetPickDetailPaging(
            picks = receivedPickDetails,
            totalPage = pagingPick.totalPages,
            totalElements = pagingPick.totalElements,
            isFirst = pagingPick.isFirst,
            isLast = pagingPick.isLast
        )
    }

    fun transformPickerId(
        isOpen: Boolean,
        pickerId: MemberId,
    ): MemberId {
        if (isOpen) {
            return pickerId
        }
        return MemberId(UNKNOWN)
    }

    fun transformPickerGender(
        isOpen: Boolean,
        pickerGender: MemberGender,
    ): MemberGender? {
        if (isOpen) {
            return pickerGender
        }
        return null
    }

    fun transformPickerPlatform(
        isOpen: Boolean,
        pickerPlatform: MemberPlatform,
    ): MemberPlatform? {
        if (isOpen) {
            return pickerPlatform
        }
        return null
    }

    fun transformPickerSecondInitialName(
        isOpen: Boolean,
        secondInitialName: String,
    ): String? {
        if (isOpen) {
            return secondInitialName
        }
        return null
    }

    fun transformPickerFullName(
        isOpen: Boolean,
        fullName: String,
    ): String? {
        if (isOpen) {
            return fullName
        }
        return null
    }

    fun transformPickerProfileImageUrl(
        pickerProfileImageUrl: String,
        pickerGender: MemberGender?,
        pickerFullName: String?,
    ): String {
        return when {
            pickerFullName != null -> pickerProfileImageUrl
            pickerGender == MemberGender.MALE -> profileImageProperties.male
            pickerGender == MemberGender.FEMALE -> profileImageProperties.female
            else -> profileImageProperties.unknown
        }
    }

    override fun getPickCount(
        questionId: QuestionId,
        memberId: MemberId,
    ): Int {
        return pickRepository.findPickDetailCount(memberId = memberId.value, questionId = questionId.value).toInt()
    }

    override fun findPickedCountByMemberId(memberId: MemberId): Int {
        return pickRepository.findPickedCountByMemberId(memberId = memberId.value).toInt()
    }

    override fun getNextPickTime(): LocalDateTime {
        val currentTime = LocalDateTime.now(ZONE_ID)
        val today = currentTime.toLocalDate()

        val pickTimes = pickTimeRepository.findAllStartTimes()

        if (pickTimes.isEmpty()) {
            throw DojoException.of(DojoExceptionType.ACTIVE_PICK_TIME_NOT_FOUND)
        }

        val nextPickTime =
            pickTimes
                .map { today.atTime(it) }
                .firstOrNull { it.isAfter(currentTime) }

        // 다음 투표 시간이 오늘 안에 있다면 반환, 아니면 내일 첫 투표 시간 반환
        return nextPickTime ?: today.plusDays(1).atTime(pickTimes.first())
    }

    override fun getReceivedSpacePicks(memberId: MemberId): List<PickService.SpacePickDetail> {
        return pickRepository.findTopRankPicksByMemberId(memberId = memberId.value, rank = defaultRankSize).map { pick ->
            val pickCount = pickRepository.findPickDetailCount(memberId = memberId.value, questionId = pick.questionId)
            PickService.SpacePickDetail(
                pickId = PickId(pick.pickId),
                questionId = QuestionId(pick.questionId),
                pickCount = pickCount.toInt(),
                pickContent = pick.questionContent,
                createdAt = pick.createdAt
            )
        }
    }

    companion object {
        private val ZONE_ID = ZoneId.of("Asia/Seoul")

        val DEFAULT_PICK =
            Pick(
                id = PickId("pickmepickme"),
                questionId = QuestionId("question"),
                questionSetId = QuestionSetId("questionSetId"),
                questionSheetId = QuestionSheetId("questionSheetId"),
                pickerId = MemberId("뽑은놈"),
                pickedId = MemberId("뽑힌놈"),
                isGenderOpen = false,
                isPlatformOpen = false,
                isMidInitialNameOpen = false,
                isFullNameOpen = false,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        private const val UNKNOWN = "UNKNOWN"
    }
}

private fun Pick.toEntity(): PickEntity {
    return PickEntity(
        id = id.value,
        questionId = questionId.value,
        questionSetId = questionSetId.value,
        questionSheetId = questionSheetId.value,
        pickerId = pickerId.value,
        pickedId = pickedId.value,
        isGenderOpen = isGenderOpen,
        isPlatformOpen = isPlatformOpen,
        isMidInitialNameOpen = isMidInitialNameOpen,
        isFullNameOpen = isFullNameOpen
    )
}

private fun PickEntity.toPick(): Pick {
    return Pick(
        id = PickId(id),
        questionId = QuestionId(questionId),
        questionSetId = QuestionSetId(questionSetId),
        questionSheetId = QuestionSheetId(questionSheetId),
        pickerId = MemberId(pickerId),
        pickedId = MemberId(pickedId),
        isGenderOpen = isGenderOpen,
        isPlatformOpen = isPlatformOpen,
        isMidInitialNameOpen = isMidInitialNameOpen,
        isFullNameOpen = isFullNameOpen,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun List<PickService.SpacePickDetail>.calculateRanks(): List<PickService.SpacePickDetail> {
    if (this.isEmpty()) return this

    // 첫 번째 조건: pickCount 내림차순 정렬
    // 두 번째 조건: createdAt 내림차순 정렬
    val sortedPicks =
        this.sortedWith(
            compareByDescending<PickService.SpacePickDetail> { it.pickCount }
                .thenByDescending { it.createdAt }
        )

    var currentRank = 1
    var previousRank = 1

    return sortedPicks.mapIndexed { index, currentPick ->
        if (index > 0) {
            val previousPick = sortedPicks[index - 1]
            // PickCount가 다르면 현재 등수 업데이트
            if (currentPick.pickCount != previousPick.pickCount) {
                currentRank = index + 1
            }
        }
        previousRank = currentRank
        currentPick.copy(rank = currentRank)
    }
}
