package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.member.entity.SocialType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class SocialLoginStrategyConfig {

    @Bean
    public Map<SocialType, SocialLoginStrategy> socialLoginStrategyMap(
            List<SocialLoginStrategy> strategies
    ) {
        return strategies.stream()
                .collect(Collectors.toMap(
                        SocialLoginStrategy::getSocialType,
                        Function.identity()
                ));
    }
}
