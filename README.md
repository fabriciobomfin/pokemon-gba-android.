# Fire Game para Android

Emulador de GBA e Nintendo DS com EmulatorJS 4.2.3, mGBA e DeSmuME 2015 empacotados no APK. Android 7.0 ou mais recente, com Android System WebView atualizado. O CI valida em Android 15 (API 35).

## Instalação e jogo

Use o APK release assinado com a chave permanente. O artefato `Fire-Game-unsigned-for-permanent-signing` é material de compilação e não deve ser instalado ou distribuído antes de assinar. Instale, toque em **Importar GBA, DS ou ZIP** e selecione seu arquivo .gba, .nds ou ZIP. Não há ROM incluída no aplicativo ou no repositório.

Depois de importar, a ROM fica no armazenamento privado do aplicativo. Todos os componentes do emulador já estão no APK; o aplicativo não solicita permissão de Internet e funciona em modo avião desde a primeira abertura. Você pode reabrir a ROM em **Abrir jogo salvo**. Use o menu do próprio jogo para salvar sua aventura; os arquivos de progresso são persistidos no armazenamento privado nativo do Android, separados por ROM, a cada segundo por padrão e ao voltar ao início. Desinstalar o app apaga ROM e progresso.

## Compilação

Java 17, Android SDK 35, Gradle 8.9 e Android Gradle Plugin 8.7.3:

```sh
python3 tools/prepare_emulator.py
gradle assembleDebug lintDebug
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
gradle connectedDebugAndroidTest
```

O workflow instala os pacotes específicos do SDK, sem solicitar o pacote removido `tools`. Os componentes do EmulatorJS são baixados **somente durante a compilação**, com versão fixa e SHA-256 verificado. Os núcleos mGBA e DeSmuME 2015 (WebGL e legado), descompactador, traduções, JavaScript e CSS são incluídos e verificados dentro do APK. Nenhum componente é baixado enquanto se joga.

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
- Esta edição usa `com.fabricio.firegame.ds` para instalar ao lado das versões anteriores. Exporte o backup pela versão anterior e importe nesta edição depois de selecionar a mesma ROM; isso transfere o histórico de checkpoints e os saves do cartucho sem apagar a instalação anterior.


### Fire Game 2.8
- Cada partida começa na horizontal; a orientação vertical continua disponível em Tela e vídeo.
- Na vertical, a imagem mantém proporção 3:2 e os controles ocupam uma área separada, com os atalhos abaixo da imagem.
- Saves de cartucho incluem as subpastas do núcleo, como mGBA. A versão anterior ignorava esses arquivos ao copiar para o armazenamento Android.
- A restauração é registrada antes do carregamento do núcleo. A gravação padrão ocorre a cada segundo e antes de pausar ou sair.
- A regressão de SRAM executa uma ROM original que grava pela memória do GBA, exporta o backup e verifica o dado em uma nova inicialização após force-stop, sem carregar checkpoint.


### Fire Game 3.0
- Importação de GBA e Nintendo DS com detecção do cabeçalho, inclusive em ZIPs. Os jogos importados ficam separados por console na biblioteca.
- Nintendo DS usa DeSmuME 2015 com firmware simulado pelo núcleo. Não é necessário baixar componentes durante o jogo.
- As duas telas ficam lado a lado na horizontal e empilhadas na vertical. A tela inferior recebe o toque, com botões X, Y e Mic disponíveis.
- Saves do cartucho DS (.dsv), checkpoints e backups usam o armazenamento nativo por ROM. Checkpoints de DS têm limite maior para acomodar a memória das duas CPUs.
- O CI executa homebrews originais de GBA e DS. Verifica as duas telas, o toque, a escrita de EEPROM pela ROM e a recuperação após force-stop com o cache de saves do navegador apagado.
- O desempenho de DS depende do aparelho, do WebView e do jogo; o teste homebrew não representa toda a biblioteca comercial.


### Fire Game 3.1 — atualizações sem reinstalação
- Identificador permanente de produção: `com.fabricio.firegame.stable`. Nunca alterar nas próximas versões.
- `versionCode` atual: 14. Aumentar em toda atualização publicada; manter os dados e formatos de backup compatíveis.
- Builds de teste usam `.testbuild` e não são distribuídos. Builds release não usam a chave debug.
- Certificado público esperado: `tools/signing-certificate.sha256`. O script `tools/sign_release.py` recusa qualquer outra chave.
- A chave PKCS12 e a senha estão no backup privado **Fire-Game-chave-privada-backup.zip** do proprietário, fora do GitHub. Não gerar outra chave, nem publicar este backup.
- Para assinar localmente, definir `FIREGAME_KEYSTORE`, `FIREGAME_PASSWORD_FILE` e `APKSIGNER_JAR`, e executar `python3 tools/sign_release.py entrada-unsigned.apk Fire-Game.apk`.
- Para assinar no Actions, cadastrar `FIREGAME_KEYSTORE_BASE64` e `FIREGAME_KEYSTORE_PASSWORD` como secrets do repositório. Sem eles, o Actions entrega apenas o material não assinado para assinatura privada; nunca substitui por chave temporária.
- O CI instala a versão de teste 13, importa jogos e saves de GBA/DS, atualiza para 14 com `adb install -r` e verifica biblioteca, preferências e save de DS após reinicialização sem cache do navegador.
- Migração inicial: esta edição instala ao lado da 3.0, cuja chave temporária não está disponível. Exportar o backup na antiga, importar a mesma ROM e o backup na 3.1. Depois disso, os próximos APKs release com a mesma chave atualizam por cima.
- O APK é completo: instala do zero quando o aplicativo está ausente e atualiza quando presente. Internet não é necessária para jogar.


### Fire Game 3.2 — uma tela DS ampliada e segunda tela opcional
- DS abre inicialmente com a tela superior ampliada e os controles sobre a imagem, preservando a proporção 4:3.
- Atalho 1↔2 alterna instantaneamente entre tela superior e inferior. A inferior ampliada recebe o toque normal.
- Atalho de janela mostra a outra tela em uma sobreposição ao vivo. A tela inferior pequena também recebe toque; arraste sua barra para reposicionar e ajuste o tamanho em Tela e vídeo.
- Tela e vídeo mantém a opção tradicional de duas telas lado a lado / empilhadas. Modo, tela selecionada, tamanho e posição são salvos por jogo.
- Não existe detecção automática de pedidos de interação: o núcleo não fornece um sinal confiável para isso.
- Mesmo identificador e certificado permanente da 3.1; versionCode 15. Instalar por cima da 3.1 sem apagar dados.

- A biblioteca abre e retorna sempre na vertical; jogos começam na horizontal e mantêm a opção de orientação nas configurações.
