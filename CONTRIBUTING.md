# Como contribuir com o Autodriver

Obrigado pelo interesse em contribuir! Este guia explica como participar.

## Reportando bugs e sugerindo melhorias

- Antes de abrir uma issue, verifique se o assunto já não foi reportado.
- Para bugs, informe: modelo do aparelho, versão do Android, versão do app Uber Driver, passos para reproduzir e, se possível, prints (oculte dados pessoais).
- Para sugestões, descreva o problema que a melhoria resolve.

## Enviando código (Pull Requests)

1. Faça um fork do repositório e crie uma branch a partir da `main`:
   `git checkout -b minha-melhoria`
2. Configure o ambiente conforme o README (Android Studio, SDK 34, Kotlin 1.9.24).
3. Faça mudanças pequenas e focadas — um assunto por PR.
4. Garanta que o projeto compila: `./gradlew assembleDebug`
5. Teste em um dispositivo Android físico sempre que possível.
6. Abra o Pull Request para a `main` explicando o que mudou e por quê.

## Padrões de código

- Kotlin seguindo as convenções oficiais (formatação padrão do Android Studio).
- Siga também as orientações do arquivo `CLAUDE.md`, quando aplicável.
- Mensagens de commit claras e no imperativo (ex.: "Corrige leitura do valor por km").
- Não envie credenciais, client IDs reais, chaves ou arquivos `local.properties`.

## Licença

Ao contribuir, você concorda que sua contribuição será licenciada sob a licença MIT do projeto (veja `LICENSE`).
