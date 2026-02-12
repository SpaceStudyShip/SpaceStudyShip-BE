package com.elipair.spacestudyship.member.repository;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.entity.Member;
import com.elipair.spacestudyship.member.entity.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);

    Optional<Member> findBySocialIdAndSocialType(String socialId, SocialType socialType);

    default Member getByMemberId(Long memberId) {
        return findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
