# AZURE-Discord-Bot
AZURE-Discord-Bot은 AZURE 관리 기능을 지원하는 디스코드 봇**입니다.  
디스코드 서버에서 간편한 명령어를 통해 AZURE VM을 관리하고, 비용 예측 및 로그 확인이 가능합니다.  

---

## 📌 주요 기능 🛠️
✅ **AZURE VM 제어** (`/azure start`, `/azure stop`)  
✅ **AZURE VM 로그 확인** (`/azure logs`)  
✅ **AZURE VM 목록 확인** (`/azure list`)  
✅ **예상 비용 조회** (`/azure cost`)  
✅ **VM 상태 변경 알림** (`/azure notify`)  

---

## 📌 명령어 사용법 💬
> `[]`는 필수 입력값, `{}`는 선택 입력값입니다.

| 명령어                    | 설명                | 예제                 |
|------------------------|-------------------|--------------------|
| `/azure start [vm_name]` | 지정한 GCP VM을 시작    | `/azure start my-vm` |
| `/azure stop [vm_name]`  | 지정한 GCP VM을 중지    | `/azure stop my-vm`  |
| `/azure logs`            | 최근 GCP VM 로그 확인   | `/azure logs`        |
| `/azure list`            | 보유 중인 GCP VM 목록 확인 | `/azure list`        |
| `/azure cost`            | 예상 GCP 비용 조회      | `/azure cost`        |
| `/azure notify`          | VM 상태 변경 시 알림 활성화 | `/azure notify`      |

---
