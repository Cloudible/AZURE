package com.azure.discord.listener;

import com.azure.discord.service.AzureVMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlashCommandListener extends ListenerAdapter {

    private final AzureVMService azureVMService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("azure")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        log.info("명령어 수신: /azure {}", subcommand);

        // 사용자와 채널 정보 가져오기
        String userId = event.getUser().getId();
        String channelId = event.getChannel().getId();

        event.deferReply().queue();

        String response;

        switch (subcommand) {
            case "start":
                String startVmName = event.getOption("vm_name").getAsString();
                response = azureVMService.startVM(startVmName, channelId, userId);
                break;

            case "stop":
                String stopVmName = event.getOption("vm_name").getAsString();
                response = azureVMService.stopVM(stopVmName, channelId, userId);
                break;

            case "list":
                response = azureVMService.listVMs();
                break;

            case "logs":
                // VM 이름 옵션 처리
                var vmNameOption = event.getOption("vm_name");
                String vmName = vmNameOption != null ? vmNameOption.getAsString() : "";
                response = azureVMService.getVMLogs(vmName);
                break;

            case "cost":
                response = azureVMService.getCostEstimate();
                break;

            case "notify":
                response = azureVMService.toggleNotifications(userId);
                break;

            default:
                response = "❌ 알 수 없는 명령어입니다.";
        }

        event.getHook().editOriginal(response).queue();
    }
}