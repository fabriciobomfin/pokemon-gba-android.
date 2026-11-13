# GBA Offline para Android

Emulador GBA com EmulatorJS 4.2.3 e mGBA empacotados no APK. Android 7.0 ou mais recente, com Android System WebView atualizado. O CI valida em Android 15 (API 35).

## Instalação e jogo

Baixe `app-debug.apk` no artefato **GBA-Offline-Android-APK** da execução verde do GitHub Actions. Instale, toque em **Importar ROM .gba** e selecione seu arquivo descompactado. Toque em **Jogar offline**. Não há ROM incluída no aplicativo ou no repositório.

Depois de importar, a ROM fica no armazenamento privado do aplicativo. Todos os componentes do emulador já estão no APK; o aplicativo não solicita permissão de Internet e funciona em modo avião desde a primeira abertura. Você pode reabrir a ROM em **Abrir jogo salvo**. Use o menu do próprio jogo para salvar sua aventura; os arquivos de progresso são persistidos no armazenamento privado nativo do Android, separados por ROM, a cada cinco segundos, ao tocar em **Gravar** e ao voltar ao início. Desinstalar o app apaga ROM e progresso.

## Compilação

Java 17, Android SDK 35, Gradle 8.9 e Android Gradle Plugin 8.7.3:

```sh
python3 tools/prepare_emulator.py
gradle assembleDebug lintDebug
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
gradle connectedDebugAndroidTest
```

O workflow instala os pacotes específicos do SDK, sem solicitar o pacote removido `tools`. Os componentes do EmulatorJS são baixados **somente durante a compilação**, com versão fixa e SHA-256 verificado. Os dois núcleos mGBA (WebGL e legado), descompactador, traduções, JavaScript e CSS são incluídos e verificados dentro do APK. Nenhum componente é baixado enquanto se joga.

A instrumentação Android importa um programa homebrew original de 256 bytes, executa mais de 120 frames, confere pixels da tela, controles de toque, a reabertura e a persistência nativa dos saves e a preservação da ROM anterior ao selecionar arquivo inválido. ROMs comerciais não são utilizadas no CI.

## Licenças e código-fonte

- Aplicativo e frontend: GNU GPL v3; texto em `app/src/main/assets/GPL-3.0.txt`.
- [EmulatorJS v4.2.3](https://github.com/EmulatorJS/EmulatorJS/tree/v4.2.3).
- [Build do núcleo](https://github.com/EmulatorJS/build), pacote mGBA de 14/06/2025 e avisos de licença incorporados no `.data`.
- [mGBA](https://github.com/EmulatorJS/mgba): Mozilla Public License 2.0.

Não adicione jogos, BIOS nem saves pessoais ao git. Eles estão ignorados, e o CI também rejeita APKs com arquivos de ROM.
