package com.mashup.dojo

import com.mashup.dojo.common.DojoApiResponse
import com.mashup.dojo.dto.Question
import com.mashup.dojo.dto.SheetResponse
import com.mashup.dojo.dto.SheetSingleResponse
import com.mashup.dojo.usecase.SheetUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sheet")
class SheetController(
    private val sheetUseCase: SheetUseCase,
) {
    // 질문지 목록 12개를 반환하는 API
    @GetMapping
    fun getQuestionSheet(): DojoApiResponse<SheetResponse> {
        val createSheets = sheetUseCase.createSheet()
        val responses =
            createSheets.map { questionSheet ->
                SheetSingleResponse(
                    currentQuestionIndex = questionSheet.currentQuestionIndex,
                    totalIndex = DEFAULT_TOTAL_INDEX,
                    Question(
                        id = questionSheet.questionId,
                        content = questionSheet.questionContent,
                        imageUrl = questionSheet.imageUrl,
                        sheetId = questionSheet.questionSheetId
                    ),
                    candidates = questionSheet.candidates
                )
            }

        return DojoApiResponse.success(
            SheetResponse(responses)
        )
    }

    companion object {
        private const val DEFAULT_TOTAL_INDEX: Long = 12L
    }
}
