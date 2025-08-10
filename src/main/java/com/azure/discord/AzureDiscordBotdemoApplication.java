package com.azure.discord;

import com.azure.discord.listener.SlashCommandListener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class AzureDiscordBotdemoApplication {

    private final JDA jda;
    private final SlashCommandListener slashCommandListener;

    public static void main(String[] args) {
        SpringApplication.run(AzureDiscordBotdemoApplication.class, args);
    }

    @PostConstruct
    public void init() {
        // 리스너 등록
        jda.addEventListener(slashCommandListener);

        // 슬래시 커맨드 등록
        jda.updateCommands().addCommands(
                Commands.slash("azure", "Azure VM 관리 명령어")
                        .addSubcommands(
                                new SubcommandData("start", "VM을 시작합니다")
                                        .addOption(OptionType.STRING, "vm_name", "시작할 VM 이름", true),

                                new SubcommandData("stop", "VM을 중지합니다")
                                        .addOption(OptionType.STRING, "vm_name", "중지할 VM 이름", true),

                                new SubcommandData("list", "VM 목록을 조회합니다"),

                                new SubcommandData("logs", "VM 로그를 확인합니다")
                                        .addOption(OptionType.STRING, "vm_name", "로그를 확인할 VM 이름", false), // false = 선택사항

                                new SubcommandData("cost", "예상 비용을 조회합니다"),

                                new SubcommandData("notify", "VM 상태 변경 알림을 설정합니다")
                        )
        ).queue(
                success -> log.info("슬래시 커맨드 등록 완료!"),
                error -> log.error("슬래시 커맨드 등록 실패: ", error)
        );
    }
}