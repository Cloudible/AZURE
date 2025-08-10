package com.azure.discord.config;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DiscordBotConfig {

    @Value("${discord.bot.token}")
    private String botToken;

    @Bean
    public JDA jda() throws InterruptedException {
        log.info("Discord Bot 초기화 중...");

        try {
            JDA jda = JDABuilder.createDefault(botToken)
                    .setActivity(Activity.playing("Azure VM 관리"))
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT  // 이게 문제의 인텐트
                    )
                    .build();

            // Bot이 준비될 때까지 대기
            jda.awaitReady();
            log.info("Discord Bot 준비 완료!");

            return jda;

        } catch (Exception e) {
            log.error("Discord Bot 초기화 실패: ", e);
            throw e;
        }
    }
}