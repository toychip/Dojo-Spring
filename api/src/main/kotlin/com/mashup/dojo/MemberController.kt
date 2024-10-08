package com.mashup.dojo

import com.mashup.dojo.common.DojoApiResponse
import com.mashup.dojo.config.security.JwtTokenService
import com.mashup.dojo.config.security.MemberPrincipalContextHolder
import com.mashup.dojo.domain.MemberId
import com.mashup.dojo.domain.MemberRelationId
import com.mashup.dojo.dto.FriendSpacePickResponse
import com.mashup.dojo.dto.MemberCreateFriendRelationRequest
import com.mashup.dojo.dto.MemberCreateRequest
import com.mashup.dojo.dto.MemberDeleteFriendRelationRequest
import com.mashup.dojo.dto.MemberLoginRequest
import com.mashup.dojo.dto.MemberProfileResponse
import com.mashup.dojo.dto.MemberSearchResponse
import com.mashup.dojo.dto.MemberUpdateRequest
import com.mashup.dojo.dto.MyProfileResponse
import com.mashup.dojo.dto.MySpacePickResponse
import com.mashup.dojo.dto.SpacePickDetail
import com.mashup.dojo.service.MemberService
import com.mashup.dojo.usecase.MemberUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger { }

@Tag(name = "Member", description = "멤버")
@RestController
class MemberController(
    private val memberUseCase: MemberUseCase,
    private val memberService: MemberService,
    private val jwtTokenService: JwtTokenService,
) {
    @PostMapping("/public/member")
    @Operation(
        summary = "[PUBLIC] 멤버 가입 API",
        description = "멤버 가입 시 사용하는 API. 현재 ID 제외(auto generation) 별도의 unique 값은 없어요.",
        responses = [
            ApiResponse(responseCode = "200", description = "생성된 멤버의 ID")
        ]
    )
    fun create(
        @RequestBody request: MemberCreateRequest,
    ): DojoApiResponse<MemberCreateResponse> {
        logger.info { "create member, request: $request" }

        val memberId =
            memberUseCase.create(
                MemberUseCase.CreateCommand(
                    fullName = request.fullName,
                    profileImageId = request.profileImageId,
                    platform = request.platform,
                    ordinal = request.ordinal,
                    gender = request.gender
                )
            )

        // 가입된 멤버에 대해서 기본 관계 생성
        memberUseCase.createDefaultMemberRelation(memberId)

        return DojoApiResponse.success(MemberCreateResponse(memberId))
    }

    @PostMapping("/public/member-login")
    @Operation(
        summary = "[PUBLIC] 멤버 로그인 API",
        description = "멤버 로그인 API, ID 값으로만 로그인하며 token을 발급받는 용도",
        responses = [
            ApiResponse(responseCode = "200", description = "생성된 멤버의 ID")
        ]
    )
    fun login(
        @RequestBody request: MemberLoginRequest,
    ): DojoApiResponse<MemberLoginResponse> {
        val id = MemberId(request.id)
        logger.info { "member-login, id: $id" }

        val member = memberService.findMemberById(id) ?: throw DojoException.of(DojoExceptionType.MEMBER_NOT_FOUND)
        val authToken = jwtTokenService.createToken(id)
        return DojoApiResponse.success(MemberLoginResponse(id, authToken.credentials))
    }

    @GetMapping("/member/{memberId}")
    @Operation(
        summary = "타인 멤버 프로필 조회 API",
        description = "멤버의 프로필을 조회하는 API."
    )
    fun getProfile(
        @PathVariable memberId: String,
    ): DojoApiResponse<MemberProfileResponse> {
        val currentMemberId = MemberPrincipalContextHolder.current().id
        val profileResponse =
            memberUseCase.findMemberById(
                targetMemberId = MemberId(memberId),
                currentMemberId = currentMemberId
            )

        return DojoApiResponse.success(
            MemberProfileResponse(
                memberId = profileResponse.memberId.value,
                profileImageUrl = profileResponse.profileImageUrl,
                memberName = profileResponse.memberName,
                platform = profileResponse.platform,
                ordinal = profileResponse.ordinal,
                isFriend = profileResponse.isFriend,
                pickCount = profileResponse.pickCount,
                friendCount = profileResponse.friendCount
            )
        )
    }

    @GetMapping("/member/me")
    @Operation(
        summary = "본인 프로필 조회 API",
        description = "본인 프로필 조회 API, header 토큰에 의해 본인을 식별해요"
    )
    fun me(): DojoApiResponse<MyProfileResponse> {
        val memberId = MemberPrincipalContextHolder.current().id

        logger.info { "read my profile, $memberId" }

        val response = memberUseCase.findMyProfile(memberId)

        return DojoApiResponse.success(
            MyProfileResponse(
                memberId = response.memberId,
                profileImageUrl = response.profileImageUrl,
                memberName = response.memberName,
                platform = response.platform,
                ordinal = response.ordinal,
                pickCount = response.pickCount,
                pickedCount = response.pickedCount,
                friendCount = response.friendCount
            )
        )
    }

    // ToDo 로직 연결 후 추후 제거
    @GetMapping("/member/mock/{memberId}")
    @Operation(
        summary = "타인 멤버 프로필 조회 API",
        description = "멤버의 프로필을 조회하는 API."
    )
    fun getProfileMock(
        @PathVariable memberId: String,
    ): DojoApiResponse<MemberProfileResponse> {
        val profileResponse = memberUseCase.findMemberByIdMock(MemberId(memberId))

        return DojoApiResponse.success(
            MemberProfileResponse(
                memberId = profileResponse.memberId.value,
                profileImageUrl = profileResponse.profileImageUrl,
                memberName = profileResponse.memberName,
                platform = profileResponse.platform,
                ordinal = profileResponse.ordinal,
                isFriend = profileResponse.isFriend,
                pickCount = profileResponse.pickCount,
                friendCount = profileResponse.friendCount
            )
        )
    }

    @PatchMapping("/member/{id}")
    @Operation(
        summary = "멤버 정보 갱신 API",
        description = "멤버 정보 수정 시 사용하는 API. 수정될 요소만 not-null로 받아요. null로 들어온 프로퍼티는 기존 값을 유지해요.",
        responses = [
            ApiResponse(responseCode = "200", description = "갱신된 멤버의 ID")
        ]
    )
    fun update(
        @PathVariable id: String,
        @RequestBody request: MemberUpdateRequest,
    ): DojoApiResponse<MemberUpdateResponse> {
        logger.info { "update member, member-id: $id, request: $request" }

        val memberId =
            memberUseCase.update(
                MemberUseCase.UpdateCommand(
                    memberId = MemberId(id),
                    profileImageId = request.profileImageId
                )
            )

        return DojoApiResponse.success(MemberUpdateResponse(memberId))
    }

    @PostMapping("/member/friend")
    @Operation(
        summary = "친구(팔로우)추가 API",
        description = "친구(팔로우) 관계 생성 API, 친구 추가 기능에 대해서 from 이 to 를 follow 합니다. 이미 follow가 존재한다면 예외를 반환해요",
        responses = [
            ApiResponse(responseCode = "200", description = "생성된 관계 id")
        ]
    )
    fun createFriend(
        @RequestBody request: MemberCreateFriendRelationRequest,
    ): DojoApiResponse<MemberRelationId> {
        return DojoApiResponse.success(memberUseCase.updateFriendRelation(MemberUseCase.UpdateFriendCommand(request.fromMemberId, request.toMemberId)))
    }

    @DeleteMapping("/member/friend")
    @Operation(
        summary = "친구(팔로우)해제 API",
        description = "from 이 to 에 대한 친구(팔로우)를 해제합니다. 이미 친구 관계가 아니라면 예외를 반환해요."
    )
    fun deleteFriend(
        @RequestBody request: MemberDeleteFriendRelationRequest,
    ): DojoApiResponse<Unit> {
        val currentMemberId = MemberPrincipalContextHolder.current().id
        val command =
            MemberUseCase.UpdateFriendCommand(
                fromId = currentMemberId,
                toId = request.toMemberId
            )
        memberUseCase.deleteFriendRelation(command)
        return DojoApiResponse.success()
    }

    @GetMapping("/member/my-space/pick")
    @Operation(
        summary = "마이 스페이스 내가 받은 픽 API",
        description = "마이스페이스 탭 중 내가 받은 픽의 대한 API입니다. 공동 등수를 자동으로 계산하고 반환합니다. Pick이 많은 순서대로 등수를 나누고, 최신순, 내림차순으로 정렬합니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "마이 스페이스 - 내가 받은 픽 TOP3")
        ]
    )
    fun mySpace(): DojoApiResponse<MySpacePickResponse> {
        val memberId = MemberPrincipalContextHolder.current().id
        val receivedMySpacePicks = memberUseCase.receivedMySpacePicks(memberId)
        val response =
            receivedMySpacePicks.map {
                SpacePickDetail(
                    pickId = it.pickId.value,
                    questionId = it.questionId.value,
                    rank = it.rank,
                    pickContent = it.pickContent,
                    pickCount = it.pickCount,
                    createdAt = it.createdAt
                )
            }

        return DojoApiResponse.success(
            MySpacePickResponse(response)
        )
    }

    @GetMapping("/member/friend-space/{friendId}/pick")
    @Operation(
        summary = "친구 스페이스 친구가 받은 픽 API",
        description = "친구 스페이스 탭 중 친구가 받은 픽의 대한 API입니다. 공동 등수를 자동으로 계산하고 반환합니다. Pick이 많은 순서대로 등수를 나누고, 최신순, 내림차순으로 정렬합니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "친구 스페이스 - 친구가 받은 픽 TOP3")
        ]
    )
    fun friendSpace(
        @PathVariable friendId: String,
    ): DojoApiResponse<FriendSpacePickResponse> {
        val receivedMySpacePicks = memberUseCase.receivedFriendSpacePicks(MemberId(friendId))
        val response =
            receivedMySpacePicks.map {
                SpacePickDetail(
                    pickId = it.pickId.value,
                    questionId = it.questionId.value,
                    rank = it.rank,
                    pickContent = it.pickContent,
                    pickCount = it.pickCount,
                    createdAt = it.createdAt
                )
            }

        return DojoApiResponse.success(
            FriendSpacePickResponse(response)
        )
    }

    @GetMapping("/member/search")
    @Operation(
        summary = "멤버 검색 API",
        description = "친구, 친구 아닌 사람 모두 검색 가능한 통합 검색 기능입니다."
    )
    fun searchMember(
        @RequestParam keyword: String,
    ): DojoApiResponse<List<MemberSearchResponse>> {
        val memberId = MemberPrincipalContextHolder.current().id
        val result = memberUseCase.searchMember(memberId, keyword)
        return DojoApiResponse.success(
            result.map {
                MemberSearchResponse(
                    memberId = it.memberId,
                    profileImageUrl = it.profileImageUrl,
                    memberName = it.memberName,
                    platform = it.platform,
                    ordinal = it.ordinal,
                    isFriend = it.isFriend
                )
            }
        )
    }

    data class MemberCreateResponse(
        val id: MemberId,
    )

    data class MemberUpdateResponse(
        val id: MemberId,
    )

    data class MemberLoginResponse(
        val id: MemberId,
        val authToken: String,
    )
}
