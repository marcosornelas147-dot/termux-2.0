# Termux 2.0

Projeto Android de navegador com terminal integrado e ponte de conexão com o Termux.

## Recursos incluídos no código
- Navegador com gerenciamento de abas e sessões WebView.
- Terminal integrado e servidor local de desenvolvimento.
- Ponte para enviar comandos ao Termux por conexão SSH.
- Área de arquivos e configurações do navegador.

## Compilar APK
A cada push na branch `main`, o GitHub Actions executa a compilação Android.

1. Abra a aba **Actions** deste repositório.
2. Entre no workflow **Build Termux 2.0 APK**.
3. Baixe o artefato **termux-2.0-debug-apk** quando a execução terminar.

Também é possível iniciar a compilação manualmente em **Actions → Build Termux 2.0 APK → Run workflow**.

## Compilação local
Requer JDK 17, Android SDK e Gradle 9.3.1:

```bash
gradle assembleDebug
```

O APK de depuração será gerado em `app/build/outputs/apk/debug/`.

## Configuração
Copie `.env.example` para `.env` e preencha somente as chaves que você realmente usar. Nunca envie chaves privadas para o GitHub.
