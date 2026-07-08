# extension-evidence-x9

Extensão para Burp Suite (Classic Extender API) que captura evidências de
pentest direto do Proxy/Repeater e publica no Conviso Platform via GraphQL,
com apoio de IA (Gemini/OpenAI/Claude) para gerar resumos, classificar
requirements e pré-preencher vulnerabilidades.

---

## Requisitos

### Sistema

| Requisito   | Versão mínima |
|-------------|---------------|
| Java        | 1.8 (a mesma JRE embutida no Burp Suite) |
| Maven       | 3.8+          |
| Burp Suite  | Professional ou Community, com suporte à Extender API clássica (`burp-extender-api:2.3`) |

> O Java target é **1.8**, não 17, porque a extensão roda dentro do processo
> do Burp Suite e precisa da mesma JRE que ele usa.

### Credenciais

Diferente do `cli-evidence-x9` (que lê um `.env`), esta extensão **não usa
variáveis de ambiente** — as credenciais são digitadas na aba **Settings**
da extensão dentro do próprio Burp e persistidas via
`IBurpExtenderCallbacks.saveExtensionSetting` (mecanismo nativo do Burp,
por perfil de usuário):

| Campo (aba Settings)        | Descrição |
|------------------------------|-----------|
| Conviso API Key              | Chave de acesso à Conviso Platform (GraphQL) |
| Company/Scope ID             | ID da empresa/escopo na Conviso (padrão: `443`) |
| Projeto                      | Selecionado a partir da lista carregada da Conviso API |
| AI Provider                  | `gemini` (padrão) \| `openai` \| `claude` |
| AI API Key                   | Chave do provider de IA escolhido |
| Idioma do relatório          | Português (padrão) ou inglês |

---

## Build

```bash
mvn clean package
```

Gera `target/conviso-extension-evidence-x9.jar` (shaded — já inclui `gson`,
pronto para carregar no Burp).

## Testes

```bash
mvn test
```

27 testes JUnit 5 + Mockito, cobrindo apenas a lógica pura/isolável sem uma
instância real do Burp (matching de requirements, classificação de
vulnerabilidades, normalização de texto de IA, extração de evidência com
`IExtensionHelpers` mockado). Não há testes de UI (Swing) nem de integração
contra a API real da Conviso ou dos provedores de IA.

> **Atenção ao volume `/Volumes/HD2`**: ele gera arquivos AppleDouble
> (`._NomeDoArquivo`) para todo arquivo escrito, inclusive `.class` dentro de
> `target/`. Se `mvn test`/`package` falhar com um erro estranho envolvendo
> `._`, rode `find . -name "._*" -delete` antes de tentar de novo.

---

## Instalação no Burp Suite

1. Rode `mvn clean package` para gerar `target/conviso-extension-evidence-x9.jar`.
2. No Burp Suite: **Extender → Extensions → Add**.
3. **Extension type**: `Java`.
4. **Extension file**: aponte para o `.jar` gerado.
5. Após carregar, uma nova aba **Conviso X9** aparece na barra principal do
   Burp, com as sub-abas:
   - **Settings** — credenciais, provider de IA e seleção de projeto.
   - **Requirements** — lista e segue requirements OWASP ASVS do projeto
     selecionado.
   - **X9** — evidências capturadas do Proxy/Repeater via menu de contexto
     (botão direito → "Send to Conviso X9" ou similar), com rascunho gerado
     por IA antes do envio.
   - **Vulnerabilities** — pré-preenchimento e criação de vulnerabilidades na
     Conviso Platform a partir de uma evidência HTTP.

## Uso

1. Configure a **Conviso API Key**, o **Company/Scope ID** e selecione o
   **projeto** na aba Settings.
2. Configure o **AI Provider** e sua respectiva **AI API Key** (necessário
   para geração de resumo/classificação).
3. No Proxy ou Repeater, clique com o botão direito em uma requisição/resposta
   de interesse e envie para a aba **X9** ou **Vulnerabilities** pelo menu de
   contexto.
4. Revise o rascunho gerado pela IA (resumo, requirement sugerido,
   categoria/pattern de vulnerabilidade) e confirme o envio para a Conviso
   Platform.

---
