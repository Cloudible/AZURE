package com.azure.discord.config;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AzureConfig {

    @Value("${azure.tenant-id}")
    private String tenantId;

    @Value("${azure.client-id}")
    private String clientId;

    @Value("${azure.client-secret}")
    private String clientSecret;

    @Value("${azure.subscription-id}")
    private String subscriptionId;

    @Bean
    public AzureResourceManager azureResourceManager() {
        log.info("Azure 연결 초기화 중...");
        log.info("Tenant ID: {}", tenantId);
        log.info("Subscription ID: {}", subscriptionId);

        try {
            // 1. 자격 증명 생성
            TokenCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(tenantId)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();

            // 2. Azure Profile 생성
            AzureProfile profile = new AzureProfile(
                    tenantId,
                    subscriptionId,
                    AzureEnvironment.AZURE
            );

            // 3. Azure Resource Manager 생성 (올바른 방식)
            AzureResourceManager manager = AzureResourceManager
                    .configure()
                    .authenticate(credential, profile)
                    .withDefaultSubscription();

            log.info("Azure 연결 성공!");
            return manager;

        } catch (Exception e) {
            log.error("Azure 연결 실패: ", e);
            throw new RuntimeException("Azure 초기화 실패", e);
        }
    }
}