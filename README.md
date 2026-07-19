# Autodriver

Aplicativo Android em Kotlin que analisa ofertas do app Uber Driver e exibe um card flutuante com o valor do trecho por km.

## Visão geral

- App Kotlin/AndroidX com `minSdk 24` e `targetSdk 34`.
- Usa `AccessibilityService` para capturar texto de ofertas do app `com.ubercab.driver`.
- Reconhece o valor total e distância disponível em ofertas e apresenta um overlay com o valor por km.
- Permite configurar limites de aceitação e recusa com base no valor por km.
- Inclui suporte a permissão de sobreposição de tela e permissão de acessibilidade.
- Tem estrutura de login com Google Identity (Google sign-in via `androidx.credentials`), mas o fluxo de login está atualmente bypassado no `MainActivity`.

## Funcionalidades

- Monitoramento de ofertas do Uber Driver via acessibilidade.
- OCR de tela usando ML Kit Text Recognition.
- Exibição de card flutuante com:
  - valor total da corrida
  - distância total
  - valor por km
  - indicador de aceitação / recusa com base nos thresholds configurados
- Ajuste do tempo que o card fica visível.
- Configuração de locais e thresholds diretamente na interface.

## Estrutura do projeto

- `app/` - módulo principal Android.
- `app/src/main/kotlin/com/example/autodriver/`
  - `MainActivity.kt` - interface principal e configuração de permissões.
  - `SettingsActivity.kt` - ajuste do tempo de overlay e teste de card.
  - `RideAccessibilityService.kt` - serviço de acessibilidade que coleta texto e dispara overlay.
  - `OverlayCardService.kt` - exibe o card de valor por km.
  - `LoginActivity.kt` - fluxo de autenticação Google.
  - `SessionManager.kt` - gerenciamento simples de sessão local.
  - `Config.kt` - configuração em memória para thresholds e locais.
- `app/src/main/AndroidManifest.xml` - permissões e serviços do app.
- `app/src/main/res/values/strings.xml` - strings, incluindo `google_web_client_id`.

## Dependências principais

- `androidx.core:core-ktx`
- `androidx.appcompat:appcompat`
- `com.google.android.material:material`
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `androidx.credentials:credentials`
- `androidx.credentials:credentials-play-services-auth`
- `com.google.android.libraries.identity.googleid:googleid`
- `com.google.mlkit:text-recognition`

## Configuração

1. Abra o projeto em Android Studio.
2. No arquivo `app/src/main/res/values/strings.xml`, substitua `REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com` pelo seu client ID de OAuth Web.
3. Certifique-se de ter o SDK Android 34 instalado.
4. Permita o uso de AndroidX e o build com Gradle 8.5.2 / Kotlin 1.9.24.

## Build e execução

Via Android Studio:

1. Abra o projeto.
2. Sync Gradle.
3. Execute em um dispositivo Android físico.

Via linha de comando:

```bash
gradlew assembleDebug
```

Para instalação e execução:

```bash
gradlew installDebug
```

## Permissões necessárias

- `android.permission.INTERNET`
- `android.permission.SYSTEM_ALERT_WINDOW` (para exibir overlay)
- A permissão de acessibilidade precisa ser ativada manualmente nas configurações de acessibilidade do Android para o serviço `RideAccessibilityService`.

## Uso

1. Instale e abra o app.
2. Conceda permissão de sobreposição de tela quando solicitado.
3. Ative o serviço de acessibilidade em Configurações > Acessibilidade.
4. Ajuste thresholds e tempo de exibição no app.
5. Abra o app Uber Driver; o serviço de acessibilidade tentará detectar ofertas e exibir o overlay.

## Observações

- O app está direcionado ao pacote `com.ubercab.driver` do Uber Driver.
- O reconhecimento de texto usa ML Kit e pode depender da qualidade do layout da tela.
- O fluxo de login está implementado, mas está desativado por `if (false && !SessionManager.isSignedIn(this))` em `MainActivity`.

## Requisitos do ambiente

- Java 8 compatível (configurado em `compileOptions` e `kotlinOptions`)
- Android Studio com suporte a Kotlin
- Android SDK Platform 34

## Licença

Este projeto não contém uma licença explícita no repositório.
