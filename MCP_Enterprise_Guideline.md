# Enterprise Guideline: Documentação de Servidores MCP
**Versão:** 1.0
**Baseado em:** Model Context Protocol Specification 2025-11-25
**Transporte:** Streamable HTTP
**Público-alvo:** Desenvolvedores e Arquitetos de Software (nível intermediário)
**Idioma dos recursos:** Nomes e descrições em en-US; documentação em pt-BR

---

## Sumário

1. [Introdução](#1-introdução)
2. [Visão Geral do Protocolo MCP](#2-visão-geral-do-protocolo-mcp)
3. [Arquitetura e Componentes](#3-arquitetura-e-componentes)
4. [Transporte: Streamable HTTP](#4-transporte-streamable-http)
5. [Ciclo de Vida da Conexão](#5-ciclo-de-vida-da-conexão)
6. [Autorização (OAuth 2.1)](#6-autorização-oauth-21)
7. [Convenções de Nomenclatura e Descrição](#7-convenções-de-nomenclatura-e-descrição)
8. [Documentando Tools](#8-documentando-tools)
9. [Documentando Resources](#9-documentando-resources)
10. [Documentando Prompts](#10-documentando-prompts)
11. [Integração com LLMs: Boas Práticas](#11-integração-com-llms-boas-práticas)
12. [Segurança](#12-segurança)
13. [Exemplos de Uso e Conexão](#13-exemplos-de-uso-e-conexão)
14. [Checklist de Conformidade](#14-checklist-de-conformidade)

---

## 1. Introdução

O **Model Context Protocol (MCP)** é um protocolo aberto que padroniza a forma como aplicações de LLM (Large Language Models) se integram com fontes de dados externas, ferramentas e sistemas. Ele define um contrato claro entre:

- **Hosts**: aplicações LLM que iniciam conexões (ex.: IDE com IA, chatbot corporativo).
- **Clients**: conectores dentro do host que gerenciam a comunicação.
- **Servers**: serviços que expõem dados, ferramentas e templates ao modelo de IA.

Este guideline cobre **servidores MCP** que utilizam o transporte **Streamable HTTP** — o padrão da especificação 2025-11-25 para ambientes de produção e integração corporativa.

### Objetivos deste guideline

- Definir **convenções de nomes e descrições** para os recursos do servidor MCP.
- Estabelecer **padrões de documentação** para facilitar a integração com LLMs.
- Fornecer **exemplos concretos** de implementação e conexão.
- Garantir **segurança e interoperabilidade** nas integrações corporativas.

> **Nota para desenvolvedores Java:** embora este guideline seja agnóstico a linguagem, os exemplos de schemas JSON Schema seguem convenções compatíveis com bibliotecas como Jackson e Hibernate Validator. As bibliotecas de referência para MCP em Java incluem o [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) e o SDK oficial de referência TypeScript para comparação de comportamentos.

---

## 2. Visão Geral do Protocolo MCP

### 2.1 Fundamentos

MCP utiliza **JSON-RPC 2.0** como formato de mensagem sobre HTTP. Toda comunicação é **stateful** por sessão e segue um ciclo de vida definido: inicialização → operação → encerramento.

### 2.2 Primitivas do Servidor

| Primitiva | Controle           | Descrição                                              | Uso típico                          |
|-----------|--------------------|--------------------------------------------------------|-------------------------------------|
| **Tools** | Controlado pelo modelo | Funções executáveis expostas ao LLM para tomar ações | Chamadas de API, consultas a banco  |
| **Resources** | Controlado pela aplicação | Dados e contexto gerenciados pelo cliente | Conteúdo de arquivos, schemas de BD |
| **Prompts** | Controlado pelo usuário | Templates de mensagens invocados pelo usuário | Slash commands, workflows guiados    |

### 2.3 Versão do Protocolo

Este guideline é baseado na versão **`2025-11-25`** da especificação MCP. Todas as requisições HTTP devem incluir o header:

```http
MCP-Protocol-Version: 2025-11-25
```

---

## 3. Arquitetura e Componentes

```
┌─────────────────────────────────────────────────────────┐
│                     HOST APPLICATION                      │
│  ┌──────────────────┐       ┌─────────────────────────┐  │
│  │   LLM Engine     │◄─────►│     MCP Client          │  │
│  └──────────────────┘       └────────────┬────────────┘  │
└───────────────────────────────────────── │ ───────────────┘
                                           │ HTTPS (Streamable HTTP)
                         ┌─────────────────▼─────────────────┐
                         │          MCP SERVER                │
                         │                                    │
                         │  ┌─────────┐  ┌──────────────┐   │
                         │  │  Tools  │  │  Resources   │   │
                         │  └─────────┘  └──────────────┘   │
                         │  ┌──────────────────────────┐     │
                         │  │        Prompts           │     │
                         │  └──────────────────────────┘     │
                         └────────────────────────────────────┘
```

### 3.1 Fluxo de Descoberta de Capacidades

Durante a inicialização, o servidor declara quais primitivas suporta. Clientes devem consumir **apenas** as capacidades declaradas.

```json
{
  "capabilities": {
    "tools": { "listChanged": true },
    "resources": { "subscribe": true, "listChanged": true },
    "prompts": { "listChanged": true },
    "logging": {}
  }
}
```

---

## 4. Transporte: Streamable HTTP

### 4.1 Endpoint MCP

O servidor **DEVE** expor um único endpoint HTTP que suporte os métodos **POST** e **GET**:

```
https://exemplo.com/mcp
```

> **Convenção corporativa:** use sempre o caminho `/mcp` como endpoint padrão, a menos que o servidor faça parte de uma API REST existente — nesse caso, use `/api/mcp` ou o prefixo já estabelecido.

### 4.2 Enviando Mensagens ao Servidor (POST)

Toda mensagem do cliente ao servidor é um **HTTP POST** ao endpoint MCP.

**Headers obrigatórios:**

```http
POST /mcp HTTP/1.1
Host: exemplo.com
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: <session-id>           (após inicialização)
Authorization: Bearer <access-token>  (quando autenticado)
```

**Comportamento da resposta:**

| Tipo de entrada | Resposta esperada |
|-----------------|-------------------|
| JSON-RPC request | `application/json` (resposta direta) OU `text/event-stream` (SSE stream) |
| JSON-RPC notification | `202 Accepted` (sem corpo) |
| JSON-RPC response | `202 Accepted` (sem corpo) |

### 4.3 Recebendo Mensagens do Servidor (GET / SSE)

O cliente pode abrir um stream SSE para receber notificações proativas do servidor:

```http
GET /mcp HTTP/1.1
Host: exemplo.com
Accept: text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: <session-id>
Authorization: Bearer <access-token>
```

**Casos de uso para o GET/SSE:**
- Receber notificações de mudança de lista (`tools/list_changed`, `resources/list_changed`).
- Receber atualizações de recursos com subscription ativa.

### 4.4 Gerenciamento de Sessão

```
┌─────────┐                              ┌─────────┐
│ Client  │                              │ Server  │
└────┬────┘                              └────┬────┘
     │  POST /mcp (initialize)               │
     │ ─────────────────────────────────────►│
     │                                       │
     │  200 OK                               │
     │  MCP-Session-Id: abc123...            │
     │ ◄─────────────────────────────────────│
     │                                       │
     │  POST /mcp (initialized notification) │
     │  MCP-Session-Id: abc123...            │
     │ ─────────────────────────────────────►│
     │                                       │
     │  202 Accepted                         │
     │ ◄─────────────────────────────────────│
     │                                       │
     │  [operações normais com session-id]   │
     │                                       │
     │  DELETE /mcp                          │
     │  MCP-Session-Id: abc123...            │
     │ ─────────────────────────────────────►│
```

**Regras do Session ID:**
- O servidor **PODE** gerar e retornar um `MCP-Session-Id` durante a inicialização.
- Deve ser **globalmente único** e **criptograficamente seguro** (ex.: UUID v4 ou JWT assinado).
- Deve conter apenas **caracteres ASCII visíveis** (0x21–0x7E).
- O cliente **DEVE** incluí-lo em todas as requisições subsequentes.
- Para encerrar a sessão, o cliente **DEVE** enviar `DELETE /mcp` com o header de sessão.

### 4.5 Resumabilidade de Streams SSE

Para suportar reconexão sem perda de mensagens:

```
data: { "jsonrpc": "2.0", ... }
id: stream-001-evt-42
retry: 3000
```

- O servidor **PODE** incluir `id` em eventos SSE para permitir retomada.
- O cliente usa o header `Last-Event-ID` ao reconectar via GET.
- O servidor **PODE** reenviar mensagens perdidas a partir do último evento recebido.

---

## 5. Ciclo de Vida da Conexão

### 5.1 Inicialização

```json
// Requisição do cliente
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "capabilities": {
      "roots": { "listChanged": true },
      "sampling": {}
    },
    "clientInfo": {
      "name": "MyEnterpriseClient",
      "title": "My Enterprise MCP Client",
      "version": "2.1.0"
    }
  }
}

// Resposta do servidor
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-11-25",
    "capabilities": {
      "tools": { "listChanged": true },
      "resources": { "subscribe": true, "listChanged": true },
      "prompts": { "listChanged": true },
      "logging": {}
    },
    "serverInfo": {
      "name": "OrderManagementMCPServer",
      "title": "Order Management MCP Server",
      "version": "1.0.0",
      "description": "MCP server exposing order management capabilities for AI agents"
    },
    "instructions": "Use tools to query and modify orders. Resources provide read-only access to catalogs and schemas."
  }
}
```

> **Campo `instructions`:** use-o para orientar o comportamento do LLM em relação ao servidor. Seja conciso e objetivo — descreva o domínio, restrições e como usar os recursos corretamente.

### 5.2 Negociação de Versão

- O cliente envia a versão mais recente que suporta.
- O servidor responde com a **mesma versão** (se suportada) ou com a **mais recente que ele suporta**.
- Se as versões forem incompatíveis, o cliente **DEVE** desconectar.

### 5.3 Timeouts

Todas as requisições **DEVEM** ter timeouts configurados:

| Tipo de operação              | Timeout recomendado |
|-------------------------------|---------------------|
| Inicialização                 | 30s                 |
| Tools leves (consulta)        | 30s                 |
| Tools pesadas (processamento) | 120s                |
| Resources/list                | 15s                 |
| Resources/read                | 30s                 |

Para cancelar uma operação em andamento, envie uma `CancelledNotification`:

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/cancelled",
  "params": {
    "requestId": 42,
    "reason": "User cancelled the operation"
  }
}
```

---

## 6. Autorização (OAuth 2.1)

### 6.1 Visão Geral

Servidores MCP com transporte HTTP **DEVEM** implementar autorização baseada em **OAuth 2.1** com **PKCE** obrigatório.

```
Client ──► MCP Server (401) ──► Authorization Server Discovery
                                        │
                                        ▼
                              OAuth 2.1 Authorization Flow
                              (PKCE com S256 obrigatório)
                                        │
                                        ▼
                              Bearer Token ──► MCP Server
```

### 6.2 Fluxo de Descoberta do Authorization Server

O servidor MCP **DEVE** responder com `401 Unauthorized` e incluir o header de descoberta:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="https://mcp.exemplo.com/.well-known/oauth-protected-resource",
                         scope="orders:read orders:write"
```

O cliente então busca o documento de metadados:

```
GET /.well-known/oauth-protected-resource
```

### 6.3 Uso do Token de Acesso

O token **DEVE** ser enviado no header `Authorization` em **todas** as requisições:

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

Tokens **NÃO DEVEM** ser incluídos em query strings.

### 6.4 Scopes

Adote o **princípio do menor privilégio**. Defina scopes granulares por domínio e operação:

| Padrão de scope      | Exemplo                    | Descrição                          |
|----------------------|----------------------------|------------------------------------|
| `{domain}:read`      | `orders:read`              | Leitura no domínio de pedidos      |
| `{domain}:write`     | `products:write`           | Escrita no domínio de produtos     |
| `{domain}:admin`     | `inventory:admin`          | Operações administrativas          |
| `{domain}:{action}`  | `reports:generate`         | Ação específica no domínio         |

---

## 7. Convenções de Nomenclatura e Descrição

Esta seção define o padrão para nomes e descrições de todos os recursos do servidor MCP. Os nomes e descrições **DEVEM** estar em **en-US**.

### 7.1 Princípios Gerais

1. **Clareza para o LLM:** O modelo de IA usa nomes e descrições para decidir quando e como invocar cada recurso. Descrições vagas ou genéricas degradam a qualidade das respostas.
2. **Consistência:** Use o mesmo estilo de nomenclatura em todo o servidor.
3. **Verbosidade calibrada:** Seja descritivo o suficiente, mas não redundante.
4. **Campos distintos:** O campo `name` é o identificador técnico (para código); `title` é o rótulo para UI humana; `description` é a explicação semântica para o LLM.

### 7.2 Convenções para Tools

#### Formato do nome

```
<verb>_<domain>[_<qualifier>]
```

| Elemento      | Regra                                                                  | Exemplos                       |
|---------------|------------------------------------------------------------------------|--------------------------------|
| **verb**      | Verbo em inglês, lowercase, que descreve a ação                        | `get`, `create`, `update`, `delete`, `list`, `search`, `validate`, `calculate`, `send`, `generate` |
| **domain**    | Substantivo ou entidade de negócio, lowercase, no singular ou plural conforme a semântica | `order`, `customer`, `product`, `invoice`, `report` |
| **qualifier** | Modificador opcional para diferenciar variantes do mesmo verbo+domínio | `by_id`, `by_status`, `summary`, `bulk` |

**Exemplos válidos:**

```
get_order
list_orders
create_order
update_order_status
delete_customer
search_products
calculate_shipping_cost
send_invoice_email
generate_sales_report
validate_payment_method
```

**Exemplos inválidos:**

```
order           ← sem verbo
GetOrder        ← PascalCase não permitido
get order       ← espaços não permitidos
getOrders       ← camelCase (preferir snake_case)
do_stuff        ← verbo genérico sem semântica
```

#### Restrições técnicas do nome

- **1 a 128 caracteres** (inclusive).
- Caracteres permitidos: `A-Z`, `a-z`, `0-9`, `_`, `-`, `.`
- **Sem espaços** ou caracteres especiais.
- **Case-sensitive**: `get_order` e `Get_Order` são tratados como tools distintas.
- Deve ser **único** dentro do servidor.

#### Formato do título (`title`)

Texto em **Title Case**, legível por humanos, para exibição em UIs:

```
"title": "Get Order by ID"
"title": "List All Orders"
"title": "Calculate Shipping Cost"
```

#### Formato da descrição

A `description` é a instrução mais importante para o LLM. Siga este template:

```
[Ação principal] [objeto/domínio]. [Quando usar]. [O que retorna]. [Restrições ou condições relevantes].
```

**Estrutura recomendada (máximo 3–5 frases):**

1. **O que faz:** descrição objetiva da ação.
2. **Quando usar:** contexto de uso para o modelo.
3. **O que retorna:** o tipo de dado ou resultado esperado.
4. **Limitações/condições:** erros esperados, pré-condições, side effects.

**Exemplos:**

```json
{
  "name": "get_order",
  "title": "Get Order by ID",
  "description": "Retrieves a single order by its unique identifier. Use this when the order ID is known and full order details are needed, including line items, status, and shipping information. Returns a complete order object. Returns an error if the order does not exist or is not accessible to the current user."
}
```

```json
{
  "name": "list_orders",
  "title": "List Orders",
  "description": "Returns a paginated list of orders filtered by optional criteria such as status, date range, or customer ID. Use this to discover orders without knowing their IDs. Supports cursor-based pagination via the 'cursor' parameter. Does not return deleted orders."
}
```

```json
{
  "name": "create_order",
  "title": "Create New Order",
  "description": "Creates a new order with the specified line items, customer, and shipping address. Use this to place orders programmatically. Validates stock availability before persisting. Returns the created order with its assigned ID and initial status 'PENDING'. This operation is not idempotent — duplicate calls will create duplicate orders."
}
```

```json
{
  "name": "calculate_shipping_cost",
  "title": "Calculate Shipping Cost",
  "description": "Estimates the shipping cost for a given order based on weight, dimensions, origin, and destination. Use this before creating an order to inform the user of shipping fees. Returns a cost breakdown by carrier option. Does not modify any data — this is a read-only estimation."
}
```

### 7.3 Convenções para Resources

#### Formato do URI

Os URIs de resources seguem o padrão [RFC 3986](https://datatracker.ietf.org/doc/html/rfc3986). Use esquemas que reflitam a natureza do dado:

| Esquema         | Uso                                              | Exemplo                                   |
|-----------------|--------------------------------------------------|-------------------------------------------|
| `file://`       | Recursos que se comportam como arquivos          | `file:///schemas/order.json`              |
| `https://`      | Recursos web que o cliente pode buscar diretamente | `https://cdn.exemplo.com/catalog.json`  |
| `{domain}://`   | Esquemas customizados por domínio de negócio     | `order://12345`, `product://catalog/v2`   |
| `git://`        | Integração com controle de versão                | `git://repo/main/src/Order.java`          |

**Convenções para esquemas customizados:**

```
{domain}://{resource-type}/{identifier}
{domain}://{resource-type}/{category}/{identifier}
```

Exemplos:
```
order://orders/12345
order://orders/status/PENDING
product://catalog/electronics
product://catalog/electronics/SKU-001
report://sales/monthly/2025-01
```

#### Formato do nome do resource

O campo `name` do resource deve ser um **identificador legível** (não o URI):

```json
{
  "uri": "order://orders/12345",
  "name": "Order #12345",
  "title": "Customer Order #12345 - Pending",
  "description": "Full order details for order 12345, including line items, customer info, and current status.",
  "mimeType": "application/json"
}
```

**Templates de resource (URI Templates - RFC 6570):**

```json
{
  "uriTemplate": "order://orders/{orderId}",
  "name": "Order by ID",
  "title": "Order Details",
  "description": "Retrieves full details for a specific order. Replace {orderId} with the numeric order identifier.",
  "mimeType": "application/json"
}
```

#### MIME Types obrigatórios

Sempre declare o `mimeType` correto:

| Tipo de dado          | MIME Type                   |
|-----------------------|-----------------------------|
| JSON estruturado      | `application/json`          |
| Texto plano           | `text/plain`                |
| Markdown              | `text/markdown`             |
| XML                   | `application/xml`           |
| CSV                   | `text/csv`                  |
| SQL Schema            | `application/sql`           |
| OpenAPI Spec          | `application/openapi+json`  |
| Arquivo binário       | `application/octet-stream`  |

### 7.4 Convenções para Prompts

#### Formato do nome

```
<domain>_<workflow>[_<variant>]
```

Exemplos:
```
order_review
order_review_detailed
customer_support_response
product_recommendation
invoice_dispute_resolution
sales_report_analysis
```

#### Formato da descrição

A descrição do prompt deve orientar o **usuário** (não o LLM) sobre quando e como usar o template:

```json
{
  "name": "order_review",
  "title": "Review Order Details",
  "description": "Guides the AI to perform a comprehensive review of an order, highlighting anomalies, risk factors, and recommended actions. Invoke this when an order requires manual inspection or approval. Accepts the order ID as a required argument."
}
```

---

## 8. Documentando Tools

### 8.1 Estrutura Completa de uma Tool

```json
{
  "name": "search_products",
  "title": "Search Product Catalog",
  "description": "Searches the product catalog using full-text search and optional filters. Use this to find products by name, category, price range, or availability status. Returns a paginated list of matching products with basic details. For full product details, use 'get_product' with the returned IDs. Returns an empty list if no products match — never throws an error for empty results.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "Full-text search query. Searches product name, description, and SKU. Minimum 2 characters.",
        "minLength": 2,
        "maxLength": 200
      },
      "category": {
        "type": "string",
        "description": "Filter by product category slug (e.g., 'electronics', 'clothing'). Optional.",
        "examples": ["electronics", "clothing", "home-appliances"]
      },
      "min_price": {
        "type": "number",
        "description": "Minimum price in BRL (Brazilian Real). Inclusive. Optional.",
        "minimum": 0
      },
      "max_price": {
        "type": "number",
        "description": "Maximum price in BRL (Brazilian Real). Inclusive. Must be greater than min_price if both are provided. Optional.",
        "minimum": 0
      },
      "in_stock_only": {
        "type": "boolean",
        "description": "When true, returns only products with available stock. Defaults to false.",
        "default": false
      },
      "limit": {
        "type": "integer",
        "description": "Maximum number of results to return. Defaults to 20. Maximum 100.",
        "minimum": 1,
        "maximum": 100,
        "default": 20
      },
      "cursor": {
        "type": "string",
        "description": "Pagination cursor returned from a previous call. Omit for the first page."
      }
    },
    "required": ["query"],
    "additionalProperties": false
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "products": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "id": { "type": "string" },
            "name": { "type": "string" },
            "sku": { "type": "string" },
            "price": { "type": "number" },
            "category": { "type": "string" },
            "in_stock": { "type": "boolean" }
          },
          "required": ["id", "name", "sku", "price", "category", "in_stock"]
        }
      },
      "total_count": { "type": "integer" },
      "next_cursor": { "type": ["string", "null"] }
    },
    "required": ["products", "total_count"]
  },
  "annotations": {
    "readOnlyHint": true,
    "idempotentHint": true
  }
}
```

### 8.2 Annotations de Tools

As annotations fornecem dicas ao LLM e ao cliente sobre o comportamento da tool:

| Annotation         | Tipo    | Padrão | Significado                                                                                   |
|--------------------|---------|--------|-----------------------------------------------------------------------------------------------|
| `readOnlyHint`     | boolean | false  | A tool não modifica estado externo (leitura pura). Use em queries e buscas.                  |
| `destructiveHint`  | boolean | true   | A tool pode causar efeitos destrutivos (delete, overwrite). Use em operações de exclusão.     |
| `idempotentHint`   | boolean | false  | Múltiplas chamadas com os mesmos argumentos têm o mesmo efeito que uma única chamada.        |
| `openWorldHint`    | boolean | true   | A tool interage com sistemas externos imprevisíveis. Use false para ferramentas determinísticas. |

**Guia de uso das annotations por tipo de operação:**

| Tipo de operação       | `readOnlyHint` | `destructiveHint` | `idempotentHint` |
|------------------------|----------------|-------------------|------------------|
| GET / busca / listagem | `true`         | `false`           | `true`           |
| CREATE                 | `false`        | `false`           | `false`          |
| UPDATE (parcial)       | `false`        | `false`           | `false`          |
| UPDATE (completo/PUT)  | `false`        | `false`           | `true`           |
| DELETE                 | `false`        | `true`            | `true`           |
| Envio de email/SMS     | `false`        | `false`           | `false`          |
| Cálculo/estimativa     | `true`         | `false`           | `true`           |

> **Atenção:** Annotations são **dicas**, não contratos. O cliente deve tratá-las como não confiáveis quando vindas de servidores não verificados. Nunca use annotations como substituto de validação real.

### 8.3 Input Schema: Boas Práticas

#### Regras obrigatórias

- O `inputSchema` **DEVE** ser um JSON Schema válido (não `null`).
- Para tools sem parâmetros, use: `{ "type": "object", "additionalProperties": false }`
- Sempre declare `"additionalProperties": false` para rejeitar campos desconhecidos.
- Marque como `required` apenas os campos verdadeiramente obrigatórios.

#### Regras de qualidade

- **Descreva cada campo** com uma `description` clara, incluindo unidades, formatos e valores de exemplo.
- **Inclua `examples`** para campos com valores enumeráveis.
- **Defina `enum`** para campos com conjunto fixo de valores.
- **Use `default`** para campos opcionais com valor padrão.
- **Especifique ranges** com `minimum`/`maximum` para numéricos e `minLength`/`maxLength` para strings.

```json
// Exemplo de campo bem documentado
"status": {
  "type": "string",
  "description": "Filter orders by current status. Use 'PENDING' for new orders awaiting processing, 'PROCESSING' for orders being prepared, 'SHIPPED' for dispatched orders, and 'DELIVERED' for completed orders.",
  "enum": ["PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"],
  "examples": ["PENDING", "SHIPPED"]
}
```

#### Output Schema

Declare `outputSchema` sempre que a tool retornar dados estruturados. Isso permite:
- Validação de respostas pelo cliente.
- Melhor parsing pelo LLM.
- Geração automática de documentação.

```json
// Tool sem parâmetros
{
  "name": "get_server_status",
  "title": "Get Server Status",
  "description": "Returns the current operational status of all integrated services. Use this to check system health before performing operations. Returns status for each service along with the last check timestamp.",
  "inputSchema": {
    "type": "object",
    "additionalProperties": false
  }
}
```

### 8.4 Tratamento de Erros em Tools

Use **dois mecanismos distintos** conforme a natureza do erro:

#### Erro de execução (retornado no resultado da tool)

Use `isError: true` para erros que o LLM pode corrigir e tentar novamente:

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Order not found: no order with ID '99999' exists. Please verify the order ID and try again. Valid orders can be discovered using the 'list_orders' tool."
      }
    ],
    "isError": true
  }
}
```

**Princípio:** mensagens de erro de execução devem ser **actionable** — orientar o LLM sobre como corrigir o problema.

#### Erro de protocolo (JSON-RPC error)

Use erros JSON-RPC para problemas estruturais que o LLM não pode corrigir:

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "error": {
    "code": -32602,
    "message": "Unknown tool: 'get_orders_all' is not a registered tool. Use 'list_orders' instead."
  }
}
```

### 8.5 Conteúdo de Resposta de Tools

As tools podem retornar diferentes tipos de conteúdo:

```json
// Texto simples
{
  "type": "text",
  "text": "Order #12345 status updated to SHIPPED successfully."
}

// Link para resource
{
  "type": "resource_link",
  "uri": "order://orders/12345",
  "name": "Order #12345",
  "description": "Full details of the updated order",
  "mimeType": "application/json"
}

// Resource embutido
{
  "type": "resource",
  "resource": {
    "uri": "order://orders/12345",
    "mimeType": "application/json",
    "text": "{\"id\": \"12345\", \"status\": \"SHIPPED\", ...}",
    "annotations": {
      "audience": ["assistant"],
      "priority": 0.9
    }
  }
}
```

---

## 9. Documentando Resources

### 9.1 Estrutura Completa de um Resource

```json
{
  "uri": "product://catalog/electronics",
  "name": "Electronics Catalog",
  "title": "Product Catalog - Electronics Category",
  "description": "Complete product catalog for the electronics category, including all active SKUs, prices, and stock levels. Updated every 15 minutes. Use this to provide the AI with product context for recommendation and inventory queries.",
  "mimeType": "application/json",
  "size": 204800,
  "annotations": {
    "audience": ["assistant"],
    "priority": 0.8,
    "lastModified": "2025-11-25T10:30:00Z"
  }
}
```

### 9.2 Annotations de Resources

| Annotation      | Valores                        | Uso                                                                              |
|-----------------|--------------------------------|----------------------------------------------------------------------------------|
| `audience`      | `["user"]`, `["assistant"]`, `["user", "assistant"]` | Define quem consome o recurso. Use `assistant` para dados de contexto do LLM, `user` para dados destinados à exibição. |
| `priority`      | `0.0` a `1.0`                  | Importância relativa. `1.0` = crítico, `0.0` = opcional. Use para orientar inclusão no contexto. |
| `lastModified`  | ISO 8601 timestamp             | Quando o recurso foi modificado pela última vez.                                  |

### 9.3 Resource Templates

Use URI Templates (RFC 6570) para resources parametrizados:

```json
{
  "uriTemplate": "order://orders/{orderId}/invoice",
  "name": "Order Invoice",
  "title": "Invoice for Order",
  "description": "Retrieves the invoice document for a specific order in PDF format. The {orderId} must be a valid numeric order identifier. Returns a binary PDF blob. Use this to provide invoice details to users upon request.",
  "mimeType": "application/pdf"
}
```

**Templates com múltiplos parâmetros:**

```json
{
  "uriTemplate": "report://sales/{year}/{month}",
  "name": "Monthly Sales Report",
  "title": "Sales Report by Month",
  "description": "Returns the aggregated sales report for the specified year and month. {year} must be a 4-digit year (e.g., 2025). {month} must be a 2-digit month (01–12). Returns a structured JSON with revenue, order count, and top products.",
  "mimeType": "application/json"
}
```

### 9.4 Subscriptions a Resources

Quando o servidor suporta `subscribe`, o cliente pode se inscrever para receber atualizações automáticas:

```json
// Inscrever-se em um resource
{
  "jsonrpc": "2.0",
  "id": 10,
  "method": "resources/subscribe",
  "params": { "uri": "order://orders/12345" }
}

// Notificação recebida quando o resource é atualizado
{
  "jsonrpc": "2.0",
  "method": "notifications/resources/updated",
  "params": { "uri": "order://orders/12345" }
}
```

> **Boas práticas:** Declare `subscribe: true` apenas para recursos com atualizações frequentes e relevantes (ex.: status de pedido, preços em tempo real). Evite subscriptions em recursos estáticos para não sobrecarregar o canal.

---

## 10. Documentando Prompts

### 10.1 Estrutura Completa de um Prompt

```json
{
  "name": "order_anomaly_review",
  "title": "Review Order for Anomalies",
  "description": "Analyzes an order for potential fraud indicators, pricing inconsistencies, or fulfillment risks. Use this prompt to trigger a structured AI review of suspicious orders. Requires the order ID. The AI will return a risk assessment with recommended actions.",
  "arguments": [
    {
      "name": "order_id",
      "description": "The unique identifier of the order to review (e.g., '12345'). Required.",
      "required": true
    },
    {
      "name": "review_level",
      "description": "Depth of the analysis: 'quick' for a summary check, 'detailed' for a full investigation including historical patterns. Defaults to 'quick'.",
      "required": false
    }
  ]
}
```

### 10.2 Formato de Resposta (prompts/get)

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "description": "Order anomaly review prompt for order #12345",
    "messages": [
      {
        "role": "user",
        "content": {
          "type": "text",
          "text": "Please perform a detailed anomaly review for order #12345. Analyze the following order data and identify any fraud indicators, pricing inconsistencies, or fulfillment risks. For each finding, provide a risk level (LOW, MEDIUM, HIGH) and a recommended action."
        }
      },
      {
        "role": "user",
        "content": {
          "type": "resource",
          "resource": {
            "uri": "order://orders/12345",
            "mimeType": "application/json",
            "text": "{ \"id\": \"12345\", \"total\": 9999.99, ... }"
          }
        }
      }
    ]
  }
}
```

### 10.3 Boas Práticas para Prompts

1. **Incorpore dados relevantes** como recursos embutidos nas mensagens do prompt — evite que o usuário precise passar dados manualmente.
2. **Defina o formato de saída esperado** dentro do texto do prompt para obter respostas consistentes do LLM.
3. **Use múltiplas mensagens** para simular contexto de conversação quando necessário.
4. **Valide argumentos** antes de montar as mensagens — retorne erro `-32602` para argumentos inválidos.

---

## 11. Integração com LLMs: Boas Práticas

### 11.1 Otimizando Descrições para LLMs

O LLM usa o conjunto de tools/resources/prompts disponíveis para decidir como responder a uma solicitação. Uma boa documentação é o fator mais determinante para a qualidade da integração.

#### Regras de ouro para descrições

| Regra | Correto | Incorreto |
|-------|---------|-----------|
| **Seja específico sobre o input esperado** | "Accepts a numeric order ID as a string (e.g., '12345')" | "Accepts an ID" |
| **Descreva o output claramente** | "Returns a JSON object with fields: id, status, total, items[]" | "Returns order data" |
| **Indique quando NÃO usar** | "Do not use this for bulk operations — use 'list_orders' instead" | (omitido) |
| **Mencione efeitos colaterais** | "Sends a confirmation email to the customer" | (omitido) |
| **Especifique unidades e formatos** | "Date in ISO 8601 format (e.g., '2025-11-25')" | "A date" |
| **Seja honesto sobre limitações** | "Returns at most 100 results per call" | (omitido) |

### 11.2 Estrutura do Campo `instructions` do Servidor

Use o campo `instructions` da resposta de inicialização para dar ao LLM uma visão geral do servidor:

```json
"instructions": "This server provides order management capabilities for an e-commerce platform. Tools prefixed with 'get_' or 'list_' are read-only and safe to call freely. Tools prefixed with 'create_', 'update_', or 'delete_' modify data and should be called only after user confirmation. All monetary values are in BRL (Brazilian Real). Order IDs are numeric strings. Always use 'list_orders' before 'get_order' when the order ID is unknown."
```

### 11.3 Agrupamento Lógico de Tools

Quando o servidor expõe muitas tools, use a convenção de namespace com ponto (`.`) para agrupá-las logicamente:

```
orders.get
orders.list
orders.create
orders.update_status
orders.cancel

products.search
products.get
products.update_price

reports.sales_summary
reports.inventory_status
```

> **Cuidado:** nomes com ponto são tecnicamente válidos mas nem todos os clientes MCP os exibem de forma agrupada. Verifique a compatibilidade com o cliente-alvo.

### 11.4 Paginação e Contexto do LLM

Para tools que retornam listas, sempre suporte paginação baseada em cursor e documente explicitamente:

```json
"cursor": {
  "type": "string",
  "description": "Opaque pagination cursor returned by a previous call to this tool. Pass the value of 'next_cursor' from the previous response to retrieve the next page. Omit or set to null for the first page."
}
```

### 11.5 Conteúdo Estruturado vs. Texto

- Prefira retornar **texto humanamente legível** para respostas que o LLM lerá e reformatará.
- Use `structuredContent` + `outputSchema` quando o cliente precisar processar o resultado programaticamente.
- Para compatibilidade, inclua **ambos**: o `structuredContent` e o JSON serializado em um `TextContent`.

```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"orderId\": \"12345\", \"status\": \"SHIPPED\", \"total\": 299.99}"
    }
  ],
  "structuredContent": {
    "orderId": "12345",
    "status": "SHIPPED",
    "total": 299.99
  }
}
```

---

## 12. Segurança

### 12.1 Validação de Origin (DNS Rebinding)

O servidor **DEVE** validar o header `Origin` em todas as conexões Streamable HTTP:

```java
// Exemplo conceitual (Java / Spring)
@Override
public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
    String origin = request.getHeader("Origin");
    if (origin != null && !isAllowedOrigin(origin)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
    }
    chain.doFilter(request, response);
}
```

Em ambientes locais, o servidor **DEVE** vincular-se apenas a `127.0.0.1`, não a `0.0.0.0`.

### 12.2 Validação de Inputs de Tools

Nunca confie nos inputs recebidos — mesmo que venham de um LLM confiável:

- Valide **todos os campos** contra o `inputSchema` declarado.
- Implique **sanitização** de strings para prevenir injection (SQL, command, path traversal).
- Aplique **rate limiting** por sessão e por tool.
- Implemente **timeouts** para cada invocação de tool.

### 12.3 Controle de Acesso

- Valide **scopes OAuth** antes de executar cada tool.
- Implemente controle de acesso baseado em **resource** (não apenas em tipo de operação).
- Registre (audit log) **todas as invocações** de tools que modificam dados.

### 12.4 Segurança de Tokens

| Requisito                          | Obrigatório |
|------------------------------------|-------------|
| Tokens apenas via header `Authorization` | Sim |
| Tokens **não** em query strings    | Sim         |
| Validar audience do token          | Sim         |
| Tokens de curta duração (< 1h)     | Recomendado |
| Refresh token rotation             | Sim (public clients) |
| PKCE com `S256`                    | Sim         |
| HTTPS em todos os endpoints        | Sim         |

### 12.5 Proteção de Resources Sensíveis

- Resources com dados pessoais (PII) **DEVEM** ter `audience: ["user"]` — não exponha ao assistente sem consentimento explícito.
- Nunca retorne secrets, tokens, ou credenciais em resources ou resultados de tools.
- Aplique o princípio do menor privilégio: retorne apenas os campos necessários para a operação.

---

## 13. Exemplos de Uso e Conexão

### 13.1 Exemplo: Inicialização e Descoberta de Tools

**Passo 1: POST de inicialização**

```http
POST /mcp HTTP/1.1
Host: api.exemplo.com
Content-Type: application/json
Accept: application/json, text/event-stream

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "capabilities": { "sampling": {} },
    "clientInfo": {
      "name": "EnterpriseAIAgent",
      "title": "Enterprise AI Agent",
      "version": "3.0.0"
    }
  }
}
```

**Resposta:**

```http
HTTP/1.1 200 OK
Content-Type: application/json
MCP-Session-Id: 7f3a9b2e-1234-4abc-8def-0a1b2c3d4e5f

{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-11-25",
    "capabilities": {
      "tools": { "listChanged": true },
      "resources": { "subscribe": true, "listChanged": true }
    },
    "serverInfo": {
      "name": "OrderManagementServer",
      "title": "Order Management MCP Server",
      "version": "2.1.0",
      "description": "MCP server for order lifecycle management"
    },
    "instructions": "Use tools to manage orders. Read-only tools (annotated with readOnlyHint: true) can be called freely. Mutating tools require user confirmation."
  }
}
```

**Passo 2: Notificação `initialized`**

```http
POST /mcp HTTP/1.1
Host: api.exemplo.com
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: 7f3a9b2e-1234-4abc-8def-0a1b2c3d4e5f

{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

**Passo 3: Listar tools disponíveis**

```http
POST /mcp HTTP/1.1
Host: api.exemplo.com
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: 7f3a9b2e-1234-4abc-8def-0a1b2c3d4e5f

{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}
```

### 13.2 Exemplo: Chamada de Tool com SSE

```http
POST /mcp HTTP/1.1
Host: api.exemplo.com
Content-Type: application/json
Accept: application/json, text/event-stream
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: 7f3a9b2e-1234-4abc-8def-0a1b2c3d4e5f
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "search_products",
    "arguments": {
      "query": "notebook",
      "category": "electronics",
      "max_price": 5000,
      "in_stock_only": true,
      "limit": 5
    }
  }
}
```

**Resposta via SSE:**

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache

id: evt-001
data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":3,"progress":0.5,"total":1,"message":"Searching catalog..."}}

id: evt-002
data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"products\":[...],\"total_count\":12,\"next_cursor\":\"abc123\"}"}],"structuredContent":{"products":[{"id":"P001","name":"Notebook Pro 15","sku":"NB-PRO-15","price":4299.99,"category":"electronics","in_stock":true}],"total_count":12,"next_cursor":"abc123"},"isError":false}}
```

### 13.3 Exemplo: Leitura de Resource

```http
POST /mcp HTTP/1.1
Host: api.exemplo.com
Content-Type: application/json
Accept: application/json
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: 7f3a9b2e-1234-4abc-8def-0a1b2c3d4e5f
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "resources/read",
  "params": {
    "uri": "product://catalog/electronics"
  }
}
```

**Resposta:**

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "contents": [
      {
        "uri": "product://catalog/electronics",
        "mimeType": "application/json",
        "text": "{\"category\":\"electronics\",\"products\":[...],\"lastUpdated\":\"2025-11-25T10:30:00Z\"}"
      }
    ]
  }
}
```

### 13.4 Exemplo: Uso de Prompt

**Listar prompts:**

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "prompts/list"
}
```

**Obter prompt preenchido:**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "prompts/get",
  "params": {
    "name": "order_anomaly_review",
    "arguments": {
      "order_id": "12345",
      "review_level": "detailed"
    }
  }
}
```

### 13.5 Exemplo: Encerramento de Sessão

```http
DELETE /mcp HTTP/1.1
Host: api.exemplo.com
MCP-Protocol-Version: 2025-11-25
MCP-Session-Id: 7f3a9b2e-1234-4abc-8def-0a1b2c3d4e5f
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

---

## 14. Checklist de Conformidade

Use este checklist ao revisar a documentação de um servidor MCP antes de publicá-lo.

### Servidor

- [ ] O endpoint MCP responde a POST e GET no mesmo path (ex.: `/mcp`)
- [ ] O header `MCP-Protocol-Version` é validado em todas as requisições
- [ ] O header `Origin` é validado para prevenir DNS rebinding
- [ ] O `serverInfo` contém `name`, `title`, `version` e `description`
- [ ] O campo `instructions` orienta o LLM sobre o domínio e as regras de uso
- [ ] As capabilities declaradas correspondem ao que o servidor implementa
- [ ] Session IDs são gerados com entropia criptográfica adequada
- [ ] Timeouts são configurados e respeitados

### Tools

- [ ] Todos os nomes seguem o padrão `<verb>_<domain>[_<qualifier>]` em snake_case
- [ ] Todos os nomes têm entre 1 e 128 caracteres
- [ ] Nenhum nome contém espaços ou caracteres especiais fora do padrão
- [ ] Cada tool tem `title` em Title Case e `description` explicativa
- [ ] O `inputSchema` é um JSON Schema válido com `type: "object"`
- [ ] Campos obrigatórios estão marcados em `required`
- [ ] `additionalProperties: false` está declarado no schema
- [ ] Campos com valores fixos usam `enum`
- [ ] Campos numéricos têm `minimum`/`maximum` quando aplicável
- [ ] Strings têm `minLength`/`maxLength` quando aplicável
- [ ] `outputSchema` está declarado para tools com retorno estruturado
- [ ] Annotations refletem corretamente o comportamento da tool (`readOnlyHint`, `destructiveHint`, `idempotentHint`)
- [ ] Erros de execução usam `isError: true` com mensagem actionable
- [ ] Erros de protocolo usam o código JSON-RPC correto

### Resources

- [ ] Todos os URIs seguem a especificação RFC 3986
- [ ] Esquemas customizados são documentados
- [ ] Cada resource tem `name`, `title` e `description`
- [ ] `mimeType` está declarado corretamente
- [ ] Annotations `audience`, `priority` e `lastModified` estão presentes quando relevante
- [ ] URI Templates (RFC 6570) são usados para resources parametrizados
- [ ] A capability `subscribe` é declarada apenas quando subscriptions são implementadas

### Prompts

- [ ] Todos os nomes seguem o padrão `<domain>_<workflow>[_<variant>]`
- [ ] Cada prompt tem `title` e `description` orientados ao usuário
- [ ] Argumentos têm `name`, `description` e `required` declarados
- [ ] As mensagens retornadas por `prompts/get` incorporam dados relevantes como recursos embutidos
- [ ] Argumentos são validados antes de montar as mensagens

### Segurança

- [ ] Autorização OAuth 2.1 com PKCE (`S256`) está implementada
- [ ] O header `WWW-Authenticate` com `resource_metadata` é retornado nos 401
- [ ] Tokens são validados (audience, expiração, scopes) em cada requisição
- [ ] Tokens NÃO são aceitos via query string
- [ ] Inputs de tools são validados contra o `inputSchema`
- [ ] Rate limiting está implementado
- [ ] Audit log está ativo para operações que modificam dados
- [ ] HTTPS é obrigatório em todos os endpoints

---

## Referências

- [Model Context Protocol Specification 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [JSON Schema 2020-12](https://json-schema.org/draft/2020-12/schema)
- [OAuth 2.1 Draft](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1-13)
- [RFC 3986 — URI Generic Syntax](https://datatracker.ietf.org/doc/html/rfc3986)
- [RFC 6570 — URI Template](https://datatracker.ietf.org/doc/html/rfc6570)
- [RFC 8707 — Resource Indicators for OAuth 2.0](https://www.rfc-editor.org/rfc/rfc8707.html)
- [RFC 9728 — OAuth 2.0 Protected Resource Metadata](https://datatracker.ietf.org/doc/html/rfc9728)
- [Server-Sent Events (SSE) Standard](https://html.spec.whatwg.org/multipage/server-sent-events.html)

---

*Este documento é um guideline vivo. Revise e atualize-o conforme a especificação MCP evolui e novos padrões corporativos são estabelecidos.*
