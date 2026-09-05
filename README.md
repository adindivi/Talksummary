# TalkSummary (토크서머리) 💬✨

카카오톡 대화 텍스트(`.txt`)를 정밀 분석하여 일자별로 실감형 메신저처럼 복원하고, **온디바이스 GGUF 로컬 AI(llama.cpp)** 및 **Google Gemini AI**를 통해 핵심 3줄 요약과 키워드를 자동 생성하는 안전한 프라이빗 로컬 대화 보관소 안드로이드 앱입니다.

---

## 🌟 주요 기능 (Key Features)

1. **🔒 100% 온디바이스 프라이버시 & 안전 보관**
   - 카카오톡 내보내기 대화 파일(`.txt`) 및 직접 복사/붙여넣기 지원
   - 모든 대화 기록은 스마트폰 내부 SQLite Room DB에만 안전하게 저장되며 외부 서버로 유출되지 않습니다.

2. **🤖 듀얼 AI 엔진 지원 (온디바이스 GGUF & Google Gemini API)**
   - **온디바이스 GGUF 로컬 LLM**: C++ 기반 `llama.cpp` JNI 네이티브 엔진 탑재, 인터넷 연결 없이도 Qwen/LLaMA GGUF 모델로 즉시 로컬 요약 수행
   - **온라인 Gemini API**: Google Gemini 2.5 Flash, 2.5 Pro, 3.5 Flash 등 최신 생성형 AI 모델 선택 및 초고속 정밀 요약

3. **📱 실감형 카카오톡 시뮬레이터 (KakaoTalk Simulator)**
   - 화자별 자동 아바타 색상 지정, 말풍선 연속 처리, 시간대별 정렬
   - 터치 한 번으로 메시지 복사 및 화자 전환 토글 지원

4. **📅 날짜별 타임라인 & 정밀 필터링**
   - 대화 일자별 메시지 건수, 참여자 목록, AI 핵심 3줄 요약 카드
   - 시작일/종료일 캘린더 피커를 통한 스마트 범위 검색 및 원터치 초기화

5. **📐 반응형 적응형 레이아웃 (Responsive Layout)**
   - 일반 스마트폰(단일 페인 탐색)부터 폴더블, 대화면 태블릿(듀얼 페인 분할 뷰)까지 완벽 대응
   - 화면 크기에 따라 0.38f : 0.62f 가변 분할 및 말풍선 너비 자동 최적화

6. **🛠️ 실시간 자가진단 & 듀얼 토스트 알림**
   - DB, 네트워크, 정규식 파서, AI 엔진 상태 실시간 인디케이터
   - Android Native Toast + 인앱 Compose 애니메이션 배너 이중 피드백

---

## 🏗️ 기술 스택 (Tech Stack)

- **언어**: Kotlin 2.0.21
- **UI 프레임워크**: Jetpack Compose (Material 3)
- **로컬 데이터베이스**: Room DB (SQLite)
- **로컬 LLM 엔진**: `llamacpp-kotlin:0.4.0` (llama.cpp JNI Native Engine)
- **온라인 AI 통신**: Retrofit 2 + OkHttp 3 + Kotlinx Serialization
- **아키텍처**: MVVM (Model-View-ViewModel), StateFlow, Coroutines
- **타깃 SDK**: Android 14 (API 34) / 최소 SDK: Android 8.0 (API 26)

---

## 🚀 빌드 및 실행 방법 (How to Run)

### 사전 요구 사항
- Android Studio Ladybug / Meerkat 이상
- JDK 17 또는 Android Studio 번들 JBR
- Android SDK 34, NDK 28+

### 프로젝트 빌드
```powershell
# Gradle Wrapper를 통한 Kotlin 컴파일
.\gradlew.bat compileDebugKotlin

# 디버그 APK 생성
.\gradlew.bat assembleDebug
```
빌드 완료 후 생성 위치: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 라이선스 (License)
This project is licensed under the Apache License 2.0.

