package com.azure.discord.service;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.PowerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureVMService {

    private final AzureResourceManager azure;
    private final JDA jda;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // 알림 설정 저장
    private final Map<String, Boolean> notificationSettings = new ConcurrentHashMap<>();
    private final Map<String, String> userChannels = new ConcurrentHashMap<>();

    /**
     * VM 시작
     */
    public String startVM(String vmName, String channelId, String userId) {
        try {
            log.info("VM 시작 요청: {}", vmName);

            VirtualMachine vm = findVMByName(vmName);
            if (vm == null) {
                return String.format("❌ VM을 찾을 수 없습니다: %s", vmName);
            }

            // 이미 실행 중인지 확인
            PowerState currentState = vm.powerState();
            if (currentState == PowerState.RUNNING) {
                return String.format("ℹ️ VM '%s'은(는) 이미 실행 중입니다.", vmName);
            }

            // 채널 정보 저장
            userChannels.put(userId, channelId);

            // 즉시 알림 설정 상태 확인 후 메시지 전송
            boolean notifyEnabled = notificationSettings.getOrDefault(userId, false);
            log.info("사용자 {} 알림 설정 상태: {}", userId, notifyEnabled);

            // 별도 스레드에서 VM 시작 처리
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("VM {} 시작 중...", vmName);
                    vm.start();  // 동기식으로 처리
                    log.info("VM {} 시작 명령 전송 완료", vmName);

                    // 성공 시 즉시 알림
                    if (notifyEnabled) {
                        sendNotification(channelId,
                                String.format("✅ **VM 시작 완료!**\n" +
                                        "VM '%s'이(가) 성공적으로 시작되었습니다.", vmName));
                    }
                } catch (Exception e) {
                    log.error("VM 시작 중 에러 발생: ", e);

                    // 에러 타입에 따른 처리
                    String errorMsg = e.getMessage();
                    if (errorMsg != null) {
                        if (errorMsg.contains("Conflict") || errorMsg.contains("409")) {
                            // 409 Conflict - 이미 처리 중
                            log.info("VM {} 이미 시작 처리 중", vmName);
                            scheduleStatusCheck(vmName, channelId, userId, true, notifyEnabled);
                        } else if (errorMsg.contains("HTTP header")) {
                            // HTTP 헤더 에러 - 실제로는 작동할 가능성이 높음
                            log.info("HTTP 헤더 에러 발생, 상태 확인 예약");
                            scheduleStatusCheck(vmName, channelId, userId, true, notifyEnabled);
                        } else {
                            // 다른 에러
                            if (notifyEnabled) {
                                sendNotification(channelId,
                                        String.format("❌ VM '%s' 시작 중 오류가 발생했습니다.\n%s",
                                                vmName, errorMsg));
                            }
                        }
                    }
                }
            });

            return String.format("⏳ **VM 시작 중...**\n\n" +
                            "VM '%s'을(를) 시작하고 있습니다. (약 1-2분 소요)\n" +
                            "%s", vmName,
                    notifyEnabled ?
                            "✅ 완료 시 알림을 보내드리겠습니다." :
                            "💡 `/azure notify`로 알림을 켜면 완료 시 알려드립니다.");

        } catch (Exception e) {
            log.error("VM 시작 실패: ", e);
            return String.format("❌ VM 시작 실패: %s", e.getMessage());
        }
    }

    /**
     * VM 중지
     */
    public String stopVM(String vmName, String channelId, String userId) {
        try {
            log.info("VM 중지 요청: {}", vmName);

            VirtualMachine vm = findVMByName(vmName);
            if (vm == null) {
                return String.format("❌ VM을 찾을 수 없습니다: %s", vmName);
            }

            // 이미 중지된 상태인지 확인
            PowerState currentState = vm.powerState();
            if (currentState == PowerState.DEALLOCATED || currentState == PowerState.STOPPED) {
                return String.format("ℹ️ VM '%s'은(는) 이미 중지되어 있습니다.", vmName);
            }

            // 채널 정보 저장
            userChannels.put(userId, channelId);

            // 즉시 알림 설정 상태 확인
            boolean notifyEnabled = notificationSettings.getOrDefault(userId, false);
            log.info("사용자 {} 알림 설정 상태: {}", userId, notifyEnabled);

            // 별도 스레드에서 VM 중지 처리
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("VM {} 중지 중...", vmName);
                    vm.deallocate();  // 동기식으로 처리
                    log.info("VM {} 중지 명령 전송 완료", vmName);

                    // 성공 시 즉시 알림
                    if (notifyEnabled) {
                        sendNotification(channelId,
                                String.format("🛑 **VM 중지 완료!**\n" +
                                        "VM '%s'이(가) 성공적으로 중지되었습니다.\n" +
                                        "💰 비용이 절약됩니다!", vmName));
                    }
                } catch (Exception e) {
                    log.error("VM 중지 중 에러 발생: ", e);

                    // 에러 타입에 따른 처리
                    String errorMsg = e.getMessage();
                    if (errorMsg != null &&
                            (errorMsg.contains("HTTP header") || errorMsg.contains("Conflict"))) {
                        // HTTP 헤더 에러 또는 Conflict - 상태 확인 예약
                        log.info("에러 발생, 상태 확인 예약");
                        scheduleStatusCheck(vmName, channelId, userId, false, notifyEnabled);
                    } else {
                        // 다른 에러
                        if (notifyEnabled) {
                            sendNotification(channelId,
                                    String.format("❌ VM '%s' 중지 중 오류가 발생했습니다.\n%s",
                                            vmName, errorMsg));
                        }
                    }
                }
            });

            return String.format("⏳ **VM 중지 중...**\n\n" +
                            "VM '%s'을(를) 중지하고 있습니다. (약 1-2분 소요)\n" +
                            "%s", vmName,
                    notifyEnabled ?
                            "✅ 완료 시 알림을 보내드리겠습니다." :
                            "💡 `/azure notify`로 알림을 켜면 완료 시 알려드립니다.");

        } catch (Exception e) {
            log.error("VM 중지 실패: ", e);
            return String.format("❌ VM 중지 실패: %s", e.getMessage());
        }
    }

    /**
     * VM 상태 확인 스케줄링
     */
    private void scheduleStatusCheck(String vmName, String channelId, String userId,
                                     boolean isStart, boolean notifyEnabled) {
        scheduler.schedule(() -> {
            try {
                log.info("VM {} 상태 확인 중...", vmName);
                VirtualMachine vm = findVMByName(vmName);
                if (vm != null) {
                    PowerState state = vm.powerState();
                    log.info("VM {} 현재 상태: {}", vmName, state);

                    if (notifyEnabled) {
                        if (isStart && state == PowerState.RUNNING) {
                            sendNotification(channelId,
                                    String.format("✅ **VM 시작 완료!**\n" +
                                            "VM '%s'이(가) 성공적으로 시작되었습니다.", vmName));
                        } else if (!isStart && state == PowerState.DEALLOCATED) {
                            sendNotification(channelId,
                                    String.format("🛑 **VM 중지 완료!**\n" +
                                            "VM '%s'이(가) 성공적으로 중지되었습니다.", vmName));
                        } else {
                            // 아직 처리 중이면 다시 확인
                            log.info("VM {} 아직 처리 중, 재확인 예약", vmName);
                            scheduleStatusCheck(vmName, channelId, userId, isStart, notifyEnabled);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("상태 확인 중 에러: ", e);
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * 예상 비용 조회
     */
    public String getCostEstimate() {
        try {
            log.info("비용 조회 중...");

            List<VirtualMachine> vms = azure.virtualMachines()
                    .list()
                    .stream()
                    .collect(Collectors.toList());

            if (vms.isEmpty()) {
                return "💰 현재 생성된 VM이 없습니다.";
            }

            StringBuilder cost = new StringBuilder("💰 **예상 VM 비용**\n\n");
            double totalMonthly = 0;

            for (VirtualMachine vm : vms) {
                double hourlyRate = getHourlyRate(vm.size().toString());
                double monthly = hourlyRate * 24 * 30;

                // 중지된 VM은 비용 계산에서 제외
                if (vm.powerState() != PowerState.RUNNING) {
                    cost.append(String.format("**%s** (%s) - 🔴 중지됨\n", vm.name(), vm.size()));
                    cost.append("   • 비용 발생 없음\n\n");
                } else {
                    totalMonthly += monthly;
                    cost.append(String.format("**%s** (%s) - 🟢 실행 중\n", vm.name(), vm.size()));
                    cost.append(String.format("   • 시간당: $%.2f\n", hourlyRate));
                    cost.append(String.format("   • 월 예상: $%.2f\n\n", monthly));
                }
            }

            cost.append(String.format("📊 **총 월 예상 비용: $%.2f**\n", totalMonthly));
            cost.append(String.format("   (한화 약 %,d원)\n\n", (int)(totalMonthly * 1300)));
            cost.append("💡 **절약 팁**: 사용하지 않는 VM은 중지하세요!");

            return cost.toString();

        } catch (Exception e) {
            log.error("비용 조회 실패: ", e);
            return "❌ 비용 조회 실패: " + e.getMessage();
        }
    }

    /**
     * VM 목록 조회
     */
    public String listVMs() {
        try {
            log.info("VM 목록 조회 중...");

            List<VirtualMachine> vms = azure.virtualMachines()
                    .list()
                    .stream()
                    .collect(Collectors.toList());

            if (vms.isEmpty()) {
                return "📋 현재 생성된 VM이 없습니다.";
            }

            StringBuilder sb = new StringBuilder("📋 **Azure VM 목록**\n\n");
            for (VirtualMachine vm : vms) {
                String status = getStatusEmoji(vm.powerState());
                sb.append(String.format("%s **%s**\n", status, vm.name()));
                sb.append(String.format("   • 리소스 그룹: %s\n", vm.resourceGroupName()));
                sb.append(String.format("   • 위치: %s\n", vm.region().label()));
                sb.append(String.format("   • 크기: %s\n", vm.size()));
                sb.append(String.format("   • 상태: %s\n\n", getPowerStateKorean(vm.powerState())));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("VM 목록 조회 실패: ", e);
            return "❌ VM 목록 조회 실패: " + e.getMessage();
        }
    }

    /**
     * VM 로그 조회
     */
    public String getVMLogs(String vmName) {
        try {
            log.info("VM 로그 조회: {}", vmName);

            if (vmName == null || vmName.isEmpty()) {
                // VM 목록 보여주기
                List<VirtualMachine> vms = azure.virtualMachines()
                        .list()
                        .stream()
                        .collect(Collectors.toList());

                if (vms.isEmpty()) {
                    return "📋 로그를 확인할 VM이 없습니다.";
                }

                StringBuilder sb = new StringBuilder("📋 **로그를 확인할 VM을 선택하세요:**\n\n");
                for (VirtualMachine vm : vms) {
                    sb.append(String.format("• `/azure logs %s`\n", vm.name()));
                }
                return sb.toString();
            }

            VirtualMachine vm = findVMByName(vmName);
            if (vm == null) {
                return String.format("❌ VM을 찾을 수 없습니다: %s", vmName);
            }

            StringBuilder logs = new StringBuilder();
            logs.append(String.format("📋 **%s VM 상태 정보**\n\n", vmName));
            logs.append(String.format("🔹 **전원 상태**: %s\n", getPowerStateKorean(vm.powerState())));
            logs.append(String.format("🔹 **프로비저닝 상태**: %s\n", vm.provisioningState()));
            logs.append(String.format("🔹 **VM ID**: %s\n", vm.vmId()));
            logs.append(String.format("🔹 **크기**: %s\n", vm.size()));
            logs.append(String.format("🔹 **OS 유형**: %s\n", vm.osType()));
            logs.append(String.format("🔹 **위치**: %s\n", vm.region().name()));
            logs.append(String.format("🔹 **리소스 그룹**: %s\n", vm.resourceGroupName()));

            // IP 정보
            String privateIp = vm.getPrimaryNetworkInterface() != null ?
                    vm.getPrimaryNetworkInterface().primaryPrivateIP() : "N/A";
            logs.append(String.format("🔹 **내부 IP**: %s\n", privateIp));

            logs.append("\n📝 **참고사항**\n");
            logs.append("• 자세한 로그는 Azure Portal > 활동 로그에서 확인하세요\n");
            logs.append("• Boot Diagnostics를 활성화하면 더 자세한 정보를 볼 수 있습니다");

            return logs.toString();

        } catch (Exception e) {
            log.error("VM 로그 조회 실패: ", e);
            return "❌ VM 로그 조회 실패: " + e.getMessage();
        }
    }

    /**
     * 알림 토글
     */
    public String toggleNotifications(String userId) {
        boolean currentStatus = notificationSettings.getOrDefault(userId, false);
        notificationSettings.put(userId, !currentStatus);

        log.info("사용자 {} 알림 설정 변경: {} -> {}", userId, currentStatus, !currentStatus);

        if (!currentStatus) {
            return "🔔 **VM 상태 변경 알림이 활성화되었습니다!**\n\n" +
                    "이제 다음 상황에서 알림을 받게 됩니다:\n" +
                    "• VM 시작 완료 시\n" +
                    "• VM 중지 완료 시\n\n" +
                    "알림은 명령을 실행한 채널로 전송됩니다.";
        } else {
            return "🔕 **VM 상태 변경 알림이 비활성화되었습니다.**";
        }
    }

    /**
     * Discord 채널에 메시지 전송
     */
    private void sendNotification(String channelId, String message) {
        try {
            log.info("알림 전송 시도 - 채널 ID: {}", channelId);
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage(message).queue(
                        success -> log.info("알림 전송 성공: {}", channelId),
                        error -> log.error("알림 전송 실패: ", error)
                );
            } else {
                log.error("채널을 찾을 수 없음: {}", channelId);
            }
        } catch (Exception e) {
            log.error("알림 전송 중 오류: ", e);
        }
    }

    /**
     * VM 이름으로 찾기
     */
    private VirtualMachine findVMByName(String vmName) {
        return azure.virtualMachines()
                .list()
                .stream()
                .filter(vm -> vm.name().equalsIgnoreCase(vmName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 전원 상태 한글 변환
     */
    private String getPowerStateKorean(PowerState state) {
        if (state == PowerState.RUNNING) return "실행 중 🟢";
        if (state == PowerState.DEALLOCATED) return "할당 해제됨 (중지됨) 🔴";
        if (state == PowerState.STOPPED) return "중지됨 🔴";
        if (state == PowerState.STARTING) return "시작 중 🟡";
        if (state == PowerState.STOPPING) return "중지 중 🟠";
        return state.toString();
    }

    /**
     * 상태에 따른 이모지 반환
     */
    private String getStatusEmoji(PowerState state) {
        if (state == PowerState.RUNNING) return "🟢";
        if (state == PowerState.DEALLOCATED) return "🔴";
        if (state == PowerState.STARTING) return "🟡";
        if (state == PowerState.STOPPING) return "🟠";
        return "⚪";
    }

    /**
     * VM 크기별 예상 요금
     */
    private double getHourlyRate(String vmSize) {
        return switch (vmSize.toLowerCase()) {
            case "standard_b1s" -> 0.0052;
            case "standard_b2s" -> 0.0208;
            case "standard_b1ms" -> 0.0104;
            case "standard_b2ms" -> 0.0416;
            case "standard_d2s_v3" -> 0.096;
            case "standard_d4s_v3" -> 0.192;
            default -> 0.05;
        };
    }
}