# OAuth2 para Aplicativos Desktop e a Necessidade de um Authorization Server

## Contexto

Este documento explica por que a integração de clientes desktop (como Claude Code, GitHub Copilot no IntelliJ,
Cursor, etc.) com o servidor MCP deste projeto exige mais do que apenas um GitHub App, e como a implementação
resolve esse problema usando Spring Boot 3.5 / Spring WebFlux / Spring Security.

---

## O Problema: Clientes Desktop Não Podem Guardar Segredos

No OAuth2 tradicional para aplicações web, o servidor guarda um `client_secret` que nunca é exposto ao usuário.
Aplicativos desktop — qualquer programa instalado na máquina do usuário — não têm esse privilégio:
qualquer segredo embutido no executável pode ser extraído.

O OAuth2 resolve isso com duas abordagens distintas para clientes:

| Tipo de cliente | Como autentica | Pode guardar secret? |
|---|---|---|
| Aplicação web (servidor) | `client_id` + `client_secret` | Sim — fica no servidor |
| Aplicativo desktop/nativo | `client_id` + PKCE | Não — é um cliente público |

**PKCE** (Proof Key for Code Exchange, RFC 7636) é o mecanismo que permite que clientes públicos
completem o fluxo OAuth2 sem um secret, provando criptograficamente que quem iniciou o fluxo é o
mesmo que está trocando o código pelo token.

---

## Por Que o GitHub App Sozinho Não Basta

Um GitHub App (ou GitHub OAuth App) resolve o problema de **identidade** — ele diz quem é o usuário.
Mas os clientes MCP desktop esperam um conjunto específico de endpoints que o GitHub não fornece:

| O que o cliente MCP espera | Fornecido pelo GitHub? |
|---|---|
| `GET /.well-known/oauth-authorization-server` (RFC 8414) | Não |
| `POST /oauth2/register` — registro dinâmico de clientes (RFC 7591) | Não |
| `POST /oauth2/token` emitindo um token do **seu** servidor | Não |
| `GET /oauth2/jwks.json` para verificação do JWT | Não |

O protocolo MCP define que, ao receber uma requisição não autenticada, o servidor responde com:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
Link: <http://localhost:8080/.well-known/oauth-authorization-server>; rel="oauth2"
```

O cliente então descobre todos os endpoints a partir desse URL de metadados e executa o fluxo
Authorization Code + PKCE autonomamente — sem nenhuma configuração manual do usuário.
O GitHub simplesmente não expõe essa interface.

---

## A Solução: Authorization Server Próprio com GitHub como Identity Provider

A abordagem implementada neste projeto separa dois papéis que frequentemente se confundem:

- **Autenticação de identidade** → delegada ao GitHub (quem é o usuário)
- **Emissão de tokens do servidor** → feita pelo próprio app (tokens que o servidor sabe validar)

```
Cliente MCP                   aiplayground                     GitHub
(Claude Code, Copilot...)     (Auth Server + Resource Server)
        │                              │                           │
        │  1. GET /mcp/sse             │                           │
        │─────────────────────────────>│                           │
        │  401 + Link: /.well-known/.. │                           │
        │<─────────────────────────────│                           │
        │                              │                           │
        │  2. GET /.well-known/oauth-  │                           │
        │     authorization-server     │                           │
        │─────────────────────────────>│                           │
        │  { authorization_endpoint,   │                           │
        │    token_endpoint, ... }      │                           │
        │<─────────────────────────────│                           │
        │                              │                           │
        │  3. POST /oauth2/register    │                           │
        │     { redirect_uris: [...] } │                           │
        │─────────────────────────────>│                           │
        │  { client_id: "xyz" }        │                           │
        │<─────────────────────────────│                           │
        │                              │                           │
        │  4. Abre o browser           │                           │
        │  GET /oauth2/authorize       │                           │
        │     ?client_id=xyz           │                           │
        │     &code_challenge=ABC      │  redirect para GitHub     │
        │     &code_challenge_method=  │──────────────────────────>│
        │       S256                   │                           │
        │─────────────────────────────>│  usuário loga no browser  │
        │                              │<──────────────────────────│
        │                              │  GitHub retorna perfil    │
        │  redirect_uri?code=CODE      │                           │
        │<─────────────────────────────│                           │
        │                              │                           │
        │  5. POST /oauth2/token       │                           │
        │     code=CODE                │  valida PKCE              │
        │     code_verifier=ORIGINAL   │  emite JWT próprio        │
        │─────────────────────────────>│                           │
        │  { access_token: JWT... }    │                           │
        │<─────────────────────────────│                           │
        │                              │                           │
        │  6. GET /mcp/sse             │  valida JWT localmente    │
        │     Authorization: Bearer JWT│  (sem chamar GitHub)      │
        │─────────────────────────────>│                           │
        │  SSE stream                  │                           │
        │<─────────────────────────────│                           │
```

---

## Como Está Implementado Neste Projeto

### Componentes e suas responsabilidades

```
config/security/oauth/
├── OAuthServerController.java      → endpoints do Authorization Server
│     ├── GET  /.well-known/oauth-authorization-server  (RFC 8414 — metadados)
│     ├── GET  /.well-known/oauth-protected-resource    (RFC 9728 — recurso protegido)
│     ├── POST /oauth2/register                         (RFC 7591 — registro dinâmico)
│     ├── GET  /oauth2/authorize                        (inicia o fluxo, redireciona ao GitHub)
│     ├── POST /oauth2/token                            (troca code + PKCE verifier por JWT)
│     └── GET  /oauth2/jwks.json                        (chave pública para verificação externa)
│
├── DynamicClientStore.java         → registro em memória de clientes OAuth2
│     └── Segue RFC 8252 §8.3: loopback URIs ignoram porta (http://localhost:PORT)
│
├── AuthorizationCodeStore.java     → códigos de autorização de uso único (TTL: 5 min)
│
├── JwtTokenService.java            → emite e valida JWTs assinados com RSA-256
│     └── Par de chaves gerado em memória na inicialização (aceito para playground)
│
└── OAuthSuccessHandler.java        → intercepta o callback do GitHub
      ├── Se há fluxo MCP pendente na sessão: emite code e redireciona ao cliente desktop
      └── Se é login direto da UI web: redireciona para /chat (comportamento padrão)

config/security/
├── GitHubBearerTokenReactiveAuthenticationManager.java → valida tokens no /mcp/**
│     ├── JWT próprio     → validado localmente (sem chamada ao GitHub)
│     └── GitHub PAT      → validado via GET /user na API do GitHub (com cache de 5 min)
│
└── SecurityConfig.java             → duas cadeias de filtros separadas por path
      ├── @Order(1) /mcp/**          → stateless, Bearer token obrigatório
      └── @Order(2) /**              → sessão, OAuth2 Login via GitHub
```

### O papel do PKCE na segurança

O PKCE resolve o problema do cliente público sem adicionar um secret:

```
1. Cliente gera   code_verifier = string aleatória longa (ex: "abc123xyz...")
2. Cliente calcula code_challenge = BASE64URL(SHA256(code_verifier))
3. Cliente envia code_challenge para o servidor no /oauth2/authorize
4. Servidor guarda code_challenge junto com o code emitido
5. Cliente envia code_verifier no /oauth2/token
6. Servidor verifica: SHA256(code_verifier) == code_challenge armazenado?
   → Sim: emite o JWT
   → Não: rejeita (ataque de interceptação de code detectado)
```

Um atacante que intercepte o `code` no redirect não consegue trocá-lo por um token
porque não tem o `code_verifier` original — que nunca trafegou pela rede.
A implementação está em `OAuthServerController.verifyPkce()`.

### Dois modos de autenticação no /mcp/**

O `GitHubBearerTokenReactiveAuthenticationManager` aceita dois tipos de token:

**Modo 1 — JWT próprio** (fluxo MCP completo com browser):
- O cliente desktop abre o browser, o usuário loga no GitHub, recebe um JWT do servidor
- As chamadas subsequentes ao MCP usam esse JWT
- Validação: local, via RSA public key — sem chamada ao GitHub
- TTL: 1 hora

**Modo 2 — GitHub PAT** (configuração manual, sem browser):
- O usuário gera um Personal Access Token no GitHub com escopo `read:user`
- Configura o token no arquivo de settings do cliente MCP
- Validação: chamada `GET /user` na API do GitHub (com cache de 5 minutos)
- TTL: não expira (tokens clássicos) ou configurável (fine-grained tokens)

```json
// .claude/settings.json (Modo 2 — PAT manual)
{
  "mcpServers": {
    "aiplayground-rag": {
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer ghp_SeuTokenAqui"
      }
    }
  }
}
```

---

## Decisões de Design e Trade-offs

### Par de chaves RSA em memória

A chave RSA usada pelo `JwtTokenService` para assinar JWTs é gerada a cada inicialização da aplicação.
Isso significa que **todos os JWTs emitidos antes de um restart se tornam inválidos**.

| | In-memory (atual) | Persistido (produção) |
|---|---|---|
| Complexidade | Baixa | Média |
| JWTs sobrevivem ao restart | Não | Sim |
| Adequado para | Playground, dev | Produto |

Para produção, a chave deve ser carregada de um secret manager (AWS Secrets Manager, Vault, etc.)
e configurada via `@Value`.

### Registro de clientes em memória

O `DynamicClientStore` armazena clientes registrados via RFC 7591 em memória. Cada restart limpa
o registro. Para o playground isso é aceitável — clientes MCP re-registram automaticamente
no próximo fluxo de autorização.

### Verificação de organização no GitHub

O `GitHubBearerTokenReactiveAuthenticationManager` verifica se o usuário é membro da organização
GitHub configurada em `app.github.organization-name`. Isso fornece controle de acesso baseado
em pertencimento à organização sem necessidade de gerenciar listas de usuários manualmente.

---

## Fluxo Resumido por Tipo de Cliente

| Cenário | Fluxo | Token resultante |
|---|---|---|
| Claude Code (primeiro uso) | Registration → Authorize → browser abre → login GitHub → JWT | JWT (1h) |
| Claude Code (uso recorrente) | Bearer JWT diretamente | — |
| GitHub Copilot (IntelliJ) | Bearer JWT ou PAT manual | JWT ou PAT |
| Script / automação | PAT manual no header | PAT |
| Usuário na UI web | OAuth2 Login redirect → sessão | Sessão (cookie) |

---

## RFCs de Referência

| RFC | Título | Onde se aplica neste projeto |
|---|---|---|
| RFC 6749 | The OAuth 2.0 Authorization Framework | Base de todo o fluxo |
| RFC 7636 | PKCE | `OAuthServerController.verifyPkce()` |
| RFC 7591 | Dynamic Client Registration | `POST /oauth2/register`, `DynamicClientStore` |
| RFC 8252 | OAuth 2.0 for Native Apps | Tratamento de loopback URIs em `DynamicClientStore` |
| RFC 8414 | Authorization Server Metadata | `GET /.well-known/oauth-authorization-server` |
| RFC 9728 | OAuth 2.0 Protected Resource Metadata | `GET /.well-known/oauth-protected-resource` |
