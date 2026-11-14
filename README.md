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

A instrumentação Android importa um programa homebrew original de 512 bytes, executa mais de 120 frames, confere pixels da tela, controles de toque, a reabertura e a persistência nativa dos saves e a preservação da ROM anterior ao selecionar arquivo inválido. ROMs comerciais não são utilizadas no CI.

## Licenças e código-fonte

- Aplicativo e frontend: GNU GPL v3; texto em `app/src/main/assets/GPL-3.0.txt`.
- [EmulatorJS v4.2.3](https://github.com/EmulatorJS/EmulatorJS/tree/v4.2.3).
- [Build do núcleo](https://github.com/EmulatorJS/build), pacote mGBA de 14/06/2025 e avisos de licença incorporados no `.data`.
- [mGBA](https://github.com/EmulatorJS/mgba): Mozilla Public License 2.0.

Não adicione jogos, BIOS nem saves pessoais ao git. Eles estão ignorados, e o CI também rejeita APKs com arquivos de ROM.


## Fire Game 2.6 — personalização

- Menu redundante do EmulatorJS removido; atalhos de configurações, velocidade, salvar e voltar ao save usam dimensões e ícones consistentes.
- Em **Controles → Mover e redimensionar botões**, toque no controle ou escolha-o na lista e ajuste seu tamanho individual (60–200%). Posições por orientação e tamanhos são mantidos ao reabrir.
- Ative **Salvar jogo na tela** e/ou **Voltar ao save na tela** em Controles.
- Em **Tela e vídeo → Área do jogo**, alterne entre o console e o jogo em toda a tela, com controles sobre a imagem. O enquadramento oferece proporção original, corte e esticamento.
- Onze temas completos incluem Roxo, Ciano, Dourado, Azul oceano, Rosa, Rubi e Grafite, além dos anteriores. Texto, console, botões e biblioteca usam a mesma paleta.
- Esta edição usa `com.fabricio.firegame.battery` para instalar ao lado das versões anteriores. Exporte o backup pela versão anterior e importe nesta edição depois de selecionar a mesma ROM; isso transfere o histórico de checkpoints e os saves do cartucho sem apagar a instalação anterior.


### Fire Game 2.8
- Cada partida começa na horizontal; a orientação vertical continua disponível em Tela e vídeo.
- Na vertical, a imagem mantém proporção 3:2 e os controles ocupam uma área separada, com os atalhos abaixo da imagem.
- Saves de cartucho incluem as subpastas do núcleo, como mGBA. A versão anterior ignorava esses arquivos ao copiar para o armazenamento Android.
- A restauração é registrada antes do carregamento do núcleo. A gravação padrão ocorre a cada segundo e antes de pausar ou sair.
- A regressão de SRAM executa uma ROM original que grava pela memória do GBA, exporta o backup e verifica o dado em uma nova inicialização após force-stop, sem carregar checkpoint.
