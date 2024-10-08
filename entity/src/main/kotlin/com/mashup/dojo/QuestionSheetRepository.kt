package com.mashup.dojo

import com.mashup.dojo.base.BaseTimeEntity
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionSheetRepository : JpaRepository<QuestionSheetEntity, String> {
    fun findAllByQuestionSetIdAndResolverId(
        questionSetId: String,
        resolverId: String,
    ): List<QuestionSheetEntity>
}

@Entity
@Table(name = "question_sheet")
class QuestionSheetEntity(
    @Id
    val id: String,
    @Column(name = "question_set_id", nullable = false)
    val questionSetId: String,
    @Column(name = "question_id", nullable = false)
    val questionId: String,
    @Column(name = "resolver_id", nullable = false)
    val resolverId: String,
    @Convert(converter = CandidateConverter::class)
    @Column(name = "candidates", nullable = false, length = 2048)
    val candidates: List<String>,
) : BaseTimeEntity()

class CandidateConverter : AttributeConverter<List<String>, String> {
    override fun convertToDatabaseColumn(attribute: List<String>): String {
        return attribute.joinToString(DELIMITER)
    }

    override fun convertToEntityAttribute(dbData: String): List<String> {
        return dbData.split(DELIMITER).toList()
    }

    companion object {
        private const val DELIMITER = ","
    }
}
