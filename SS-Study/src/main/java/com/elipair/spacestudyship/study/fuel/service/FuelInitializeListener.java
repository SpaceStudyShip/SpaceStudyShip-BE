package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.member.event.MemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FuelInitializeListener {

    private final FuelService fuelService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMemberCreated(MemberCreatedEvent event) {
        log.info("[Fuel] MemberCreatedEvent 수신 | memberId={}", event.memberId());
        fuelService.initialize(event.memberId());
    }
}
