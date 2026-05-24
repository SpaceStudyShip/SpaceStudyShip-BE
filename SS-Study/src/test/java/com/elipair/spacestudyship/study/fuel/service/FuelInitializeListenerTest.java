package com.elipair.spacestudyship.study.fuel.service;

import com.elipair.spacestudyship.member.event.MemberCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FuelInitializeListenerTest {

    @Mock FuelService fuelService;
    @InjectMocks FuelInitializeListener listener;

    @Test
    @DisplayName("MemberCreatedEvent 수신 시 fuelService.initialize 호출")
    void onMemberCreated_callsInitialize() {
        listener.onMemberCreated(new MemberCreatedEvent(42L));

        verify(fuelService).initialize(42L);
    }
}
