package com.elipair.spacestudyship.auth.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    @Value("${firebase.admin-sdk-path}")
    private Resource credentialsResource;

    @PostConstruct
    public void initializeFirebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[FirebaseConfig] FirebaseApp 이미 초기화됨, 스킵");
            return;
        }
        try (InputStream stream = credentialsResource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[FirebaseConfig] FirebaseApp 초기화 완료");
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}
