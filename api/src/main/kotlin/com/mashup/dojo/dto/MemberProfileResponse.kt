package com.mashup.dojo.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "프로필 응답 Response")
data class MemberProfileResponse(
    @Schema(description = "유저 id")
    val memberId: String,
    @Schema(description = "유저 프로필 이미지 url")
    val profileImageUrl: String,
    @Schema(description = "유저 이름")
    val memberName: String,
    @Schema(description = "유저 플랫폼")
    val platform: String,
    @Schema(description = "유저 기수")
    val ordinal: Int,
    @Schema(description = "현재 유저와 찾은 유저의 친구 여부")
    val isFriend: Boolean,
    @Schema(description = "유저가 받은 픽 개수")
    val pickCount: Int,
    @Schema(description = "유저의 친구 수")
    val friendCount: Int,
)
