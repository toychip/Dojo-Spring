package com.mashup.dojo.usecase

import com.mashup.dojo.DojoException
import com.mashup.dojo.DojoExceptionType
import com.mashup.dojo.Status
import com.mashup.dojo.domain.CoinUseDetail
import com.mashup.dojo.domain.CoinUseType
import com.mashup.dojo.domain.MemberId
import com.mashup.dojo.domain.PickId
import com.mashup.dojo.domain.PickOpenItem
import com.mashup.dojo.domain.PickSort
import com.mashup.dojo.domain.QuestionId
import com.mashup.dojo.domain.QuestionSetId
import com.mashup.dojo.domain.QuestionSheetId
import com.mashup.dojo.service.CoinService
import com.mashup.dojo.service.ImageService
import com.mashup.dojo.service.MemberService
import com.mashup.dojo.service.NotificationService
import com.mashup.dojo.service.PickService
import com.mashup.dojo.service.QuestionService
import com.mashup.dojo.usecase.PickUseCase.GetReceivedPickPagingCommand
import com.mashup.dojo.usecase.PickUseCase.OpenPickCommand
import com.mashup.dojo.usecase.PickUseCase.PickOpenInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

interface PickUseCase {
    data class GetReceivedPickPagingCommand(
        val memberId: MemberId,
        val sort: PickSort,
        val pageNumber: Int,
        val pageSize: Int,
    )

    data class GetPagingPickCommand(
        val memberId: MemberId,
        val questionId: QuestionId,
        val pageNumber: Int,
        val pageSize: Int,
    )

    data class GetPickDetailPaging(
        val questionId: QuestionId,
        val questionContent: String,
        val questionEmojiImageUrl: String,
        val totalReceivedPickCount: Int,
        val picks: List<PickService.GetReceivedPickDetail>,
        val totalPage: Int,
        val totalElements: Long,
        val isFirst: Boolean,
        val isLast: Boolean,
    )

    data class CreatePickCommand(
        val questionSheetId: QuestionSheetId,
        val questionSetId: QuestionSetId,
        val questionId: QuestionId,
        val pickerId: MemberId,
        val pickedId: MemberId,
        val skip: Boolean,
    )

    data class OpenPickCommand(
        val pickId: PickId,
        val pickedId: MemberId,
        val pickOpenItem: PickOpenItem,
    )

    data class PickOpenInfo(
        val pickId: PickId,
        val pickOpenItem: PickOpenItem,
        val pickOpenValue: String,
        val pickOpenImageUrl: String,
    )

    data class CreatePickInfo(
        val pickId: PickId,
        val coin: Int,
    )

    fun getReceivedPickList(command: GetReceivedPickPagingCommand): PickService.GetPickPaging

    fun createPick(command: CreatePickCommand): CreatePickInfo

    fun getNextPickTime(): LocalDateTime

    fun openPick(openPickCommand: OpenPickCommand): PickOpenInfo

    fun getReceivedPickDetailPaging(command: GetPagingPickCommand): GetPickDetailPaging
}

@Component
class DefaultPickUseCase(
    @Value("\${dojo.questionSet.size}")
    private val questionSetSize: Int,
    private val pickService: PickService,
    private val questionService: QuestionService,
    private val imageService: ImageService,
    private val memberService: MemberService,
    private val notificationService: NotificationService,
    @Value("\${dojo.coin.solvedPick}")
    private val provideCoinByCompletePick: Int,
    private val coinUseCase: CoinUseCase,
    private val coinService: CoinService,
) : PickUseCase {
    override fun getReceivedPickList(command: GetReceivedPickPagingCommand): PickService.GetPickPaging {
        return pickService.getReceivedPickPaging(
            pickedMemberId = command.memberId,
            sort = command.sort,
            pageNumber = command.pageNumber,
            pageSize = command.pageSize
        )
    }

    override fun createPick(command: PickUseCase.CreatePickCommand): PickUseCase.CreatePickInfo {
        val question =
            questionService.getQuestionById(command.questionId)
                ?: throw DojoException.of(DojoExceptionType.NOT_EXIST, "NOT EXIST QUESTION ID ${command.questionId}")

        val questionSet = questionService.getQuestionSetById(command.questionSetId) ?: throw DojoException.of(DojoExceptionType.QUESTION_SET_NOT_EXIST)

        val createPickInfo: PickUseCase.CreatePickInfo

        if (command.skip) {
            val pickId =
                pickService.create(
                    questionId = question.id,
                    questionSetId = questionSet.id,
                    questionSheetId = command.questionSheetId,
                    pickerMemberId = command.pickerId,
                    pickedMemberId = MemberId("SKIP")
                )
            createPickInfo = PickUseCase.CreatePickInfo(pickId, 0)
        } else {
            val pickedMember =
                memberService.findMemberById(command.pickedId)
                    ?: throw DojoException.of(DojoExceptionType.NOT_EXIST, "NOT EXIST PICKED MEMBER ID ${command.pickedId}")

            val pickId =
                pickService.create(
                    questionId = question.id,
                    questionSetId = questionSet.id,
                    questionSheetId = command.questionSheetId,
                    pickerMemberId = command.pickerId,
                    pickedMemberId = pickedMember.id
                ).apply {
                    notificationService.notifyPicked(
                        pickId = this,
                        target = pickedMember,
                        questionId = question.id
                    )
                }

            coinUseCase.earnCoin(CoinUseCase.EarnCoinCommand(command.pickerId, provideCoinByCompletePick.toLong()))
            createPickInfo = PickUseCase.CreatePickInfo(pickId, provideCoinByCompletePick)
        }

        return createPickInfo
    }

    @Transactional
    override fun openPick(openPickCommand: OpenPickCommand): PickOpenInfo {
        val coin = coinService.getCoin(openPickCommand.pickedId) ?: throw DojoException.of(DojoExceptionType.NOT_EXIST, "유저의 코인정보가 없습니다")
        val updatedCoin = coin.useCoin(openPickCommand.pickOpenItem.cost.toLong())

        coinService.updateCoin(CoinUseType.USED, CoinUseDetail.REASON_USED_FOR_OPEN_PICK, openPickCommand.pickOpenItem.cost, updatedCoin)

        return pickService.openPick(
            openPickCommand.pickId,
            openPickCommand.pickedId,
            openPickCommand.pickOpenItem
        ).let { PickOpenInfo(openPickCommand.pickId, openPickCommand.pickOpenItem, it.pickOpenValue, it.pickOpenImageUrl) }
    }

    override fun getReceivedPickDetailPaging(command: PickUseCase.GetPagingPickCommand): PickUseCase.GetPickDetailPaging {
        val question =
            questionService.getQuestionById(command.questionId)
                ?: throw DojoException.of(DojoExceptionType.NOT_EXIST, "등록되지 않은 QuestionId 입니다. QuestionId: [${command.questionId}]")

        val imageUrl =
            imageService.load(question.emojiImageId)?.url
                ?: throw DojoException.of(DojoExceptionType.NOT_EXIST, "해당하는 이미지를 찾을 수 없습니다. EmojiImageId: [${question.emojiImageId}]")

        val pickCount: Int = pickService.getPickCount(question.id, command.memberId)

        val receivedPickPaging = pickService.getPickDetailPaging(question.id, command.memberId, command.pageNumber, command.pageSize)

        return PickUseCase.GetPickDetailPaging(
            questionId = question.id,
            questionContent = question.content,
            questionEmojiImageUrl = imageUrl,
            totalReceivedPickCount = pickCount,
            picks = receivedPickPaging.picks,
            totalPage = receivedPickPaging.totalPage,
            totalElements = receivedPickPaging.totalElements,
            isFirst = receivedPickPaging.isFirst,
            isLast = receivedPickPaging.isLast
        )
    }

    override fun getNextPickTime(): LocalDateTime {
        val nextOperatingQuestionSet =
            questionService.getNextOperatingQuestionSet(Status.READY)
                ?: throw DojoException.of(DojoExceptionType.QUESTION_SET_NOT_READY)

        return nextOperatingQuestionSet.publishedAt
    }
}
