package com.elipair.spacestudyship.auth.dto;

import com.elipair.spacestudyship.member.entity.Member;

public record AuthMemberDto(Member member, boolean isNewMember) {}
