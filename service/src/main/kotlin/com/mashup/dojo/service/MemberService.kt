package com.mashup.dojo.service

import com.mashup.dojo.DojoException
import com.mashup.dojo.DojoExceptionType
import com.mashup.dojo.MemberEntity
import com.mashup.dojo.MemberRepository
import com.mashup.dojo.domain.ImageId
import com.mashup.dojo.domain.Member
import com.mashup.dojo.domain.MemberGender
import com.mashup.dojo.domain.MemberId
import com.mashup.dojo.domain.MemberPlatform
import com.mashup.dojo.domain.MemberRelation
import com.mashup.dojo.domain.MemberRelationId
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

interface MemberService {
    fun findMemberById(memberId: MemberId): Member?

    fun create(command: CreateMember): MemberId

    fun update(command: UpdateMember): MemberId

    fun findAllMember(): List<Member>

    fun searchMember(
        memberId: MemberId,
        keyword: String,
    ): List<Member>

    fun findAllByIds(memberIds: List<MemberId>): List<Member>

    fun findByFullNameAndPlatform(
        fullName: String,
        platform: MemberPlatform,
    ): Member

    fun findMemberByFullName(fullName: String): Member

    data class CreateMember(
        val fullName: String,
        val profileImageId: ImageId?,
        val platform: MemberPlatform,
        val ordinal: Int,
        val gender: MemberGender,
    )

    data class UpdateMember(
        val memberId: MemberId,
        val profileImageId: ImageId?,
    )
}

@Service
class DefaultMemberService(
    private val memberRepository: MemberRepository,
) : MemberService {
    private fun mockMemberRelation(
        fromId: MemberId,
        toId: MemberId,
    ): MemberRelation {
        // 후보자 생성
        // 여기에 필요한 로직을 추가하세요.

        return MemberRelation(
            id = MemberRelationId("MemberRelationId"),
            fromId = fromId,
            toId = toId,
            lastUpdatedAt = LocalDateTime.now()
        )
    }

    override fun findMemberById(memberId: MemberId): Member? {
        val member = memberRepository.findById(memberId.value)
        return member.getOrNull()?.toMember()
    }

    override fun create(command: MemberService.CreateMember): MemberId {
        val member =
            Member.create(
                fullName = command.fullName,
                profileImageId = command.profileImageId,
                platform = command.platform,
                gender = command.gender,
                ordinal = command.ordinal
            )

        val id = memberRepository.save(member.toEntity()).id
        return MemberId(id)
    }

    override fun update(command: MemberService.UpdateMember): MemberId {
        val member = findMemberById(command.memberId) ?: throw DojoException.of(DojoExceptionType.MEMBER_NOT_FOUND)

        val id =
            memberRepository.save(
                member.update(
                    profileImageId = command.profileImageId
                ).toEntity()
            ).id

        return MemberId(id)
    }

    override fun findAllMember(): List<Member> {
        return memberRepository.findAll()
            .map { it.toMember() }
    }

    override fun searchMember(
        memberId: MemberId,
        keyword: String,
    ): List<Member> {
        return memberRepository.findByNameContaining(memberId.value, keyword).map { it.toMember() }
    }

    override fun findAllByIds(memberIds: List<MemberId>): List<Member> {
        return memberRepository.findAllById(memberIds.map { it.value }).map { it.toMember() }
    }

    override fun findByFullNameAndPlatform(
        fullName: String,
        platform: MemberPlatform,
    ): Member {
        return memberRepository.findSingleMemberByFullNameAndPlatform(fullName, platform.name).toMember()
    }

    override fun findMemberByFullName(fullName: String): Member {
        return memberRepository.findSingleMemberByFullName(fullName).toMember()
    }

    private fun mockMember(memberId: MemberId) =
        Member(
            memberId, "임준형", "ㅈ", ImageId("123456"), MemberPlatform.SPRING, 14, MemberGender.MALE, LocalDateTime.now(), LocalDateTime.now()
        )
}

private fun Member.toEntity(): MemberEntity {
    return MemberEntity(
        id = id.value,
        fullName = fullName,
        secondInitialName = secondInitialName,
        profileImageId = profileImageId.value,
        platform = platform.name,
        ordinal = ordinal,
        gender = gender.name
    )
}

private fun MemberEntity.toMember(): Member {
    val platform = MemberPlatform.findByValue(platform)
    val gender = MemberGender.findByValue(gender)

    return Member(
        id = MemberId(id),
        fullName = fullName,
        secondInitialName = secondInitialName,
        profileImageId = ImageId(profileImageId),
        ordinal = ordinal,
        platform = platform,
        gender = gender,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
