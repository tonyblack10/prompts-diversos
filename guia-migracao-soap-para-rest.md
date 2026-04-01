# Guia de Migração: SOAP para REST

**Serviço:** `[Nome do Serviço]`
**Versão:** 1.0
**Data:** 2026-04-01
**Público-alvo:** Desenvolvedores e engenheiros que consomem `[Nome do Serviço]` via SOAP

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Por Que Migrar? SOAP vs. REST em Microsserviços](#por-que-migrar)
3. [Diferenças Principais](#diferenças-principais)
4. [Mudanças na Autenticação](#mudanças-na-autenticação)
5. [Mapeamento de Métodos SOAP para Endpoints REST](#mapeamento-soap-para-rest)
6. [Referência de Migração do SDK](#referência-de-migração-do-sdk)
7. [Mudanças no Formato de Requisição e Resposta](#mudanças-no-formato-de-requisição-e-resposta)
8. [Tratamento de Erros](#tratamento-de-erros)
9. [Checklist de Migração](#checklist-de-migração)
10. [Suporte e Contatos](#suporte-e-contatos)

---

## Visão Geral

A equipe do `[Nome do Serviço]` está encerrando sua interface SOAP em **[Data de Descontinuação]**. Todos os clientes devem migrar para a nova API REST até essa data. Este guia apresenta cada mudança necessária com comparações de código lado a lado, uma tabela completa de mapeamento de métodos e as chamadas equivalentes no SDK para agilizar sua transição.

A nova API REST está disponível em:

```
URL Base: https://api.[service-domain].com/v1
```

O endpoint SOAP legado permanecerá disponível (somente leitura, sem novos recursos) até **[Data de Desativação]**, após a qual será descomissionado definitivamente.

---

## Por Que Migrar?

### Vantagens do REST sobre o SOAP em uma Arquitetura de Microsserviços

#### 1. Payloads Mais Leves

Mensagens SOAP são encapsuladas em XML, verbosas e carregam uma sobrecarga significativa — cabeçalhos, namespaces e definições de schema podem ser muito maiores do que o payload em si. O REST usa JSON por padrão, que é compacto, legível por humanos e interpretado nativamente por qualquer runtime moderno. Em microsserviços de alto throughput, isso reduz diretamente a latência e os custos de banda.

#### 2. Statelessness e Escalabilidade Horizontal

O REST é inerentemente stateless: cada requisição contém todas as informações necessárias para ser processada. Isso se alinha perfeitamente com microsserviços, onde instâncias de serviço são efêmeras e intercambiáveis. Sessões SOAP e extensões stateful do WS-* amarram os clientes a instâncias específicas de servidor, dificultando o balanceamento de carga e o auto-scaling.

> **Para quem está começando:** *Stateless* significa que o servidor não guarda nenhuma memória da sua requisição anterior. Cada chamada é independente e precisa conter todas as informações necessárias — como um formulário que você preenche do zero a cada vez.

#### 3. Semântica HTTP Nativa

O REST utiliza os verbos HTTP (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`) e os códigos de status para comunicar intenção e resultado. Microsserviços, API gateways, proxies e CDNs entendem HTTP nativamente — cache de respostas `GET`, roteamento por método e rate limiting por caminho funcionam sem middleware customizado.

#### 4. Compatibilidade com o Ecossistema

O ecossistema REST é significativamente mais amplo: ferramentas OpenAPI/Swagger geram SDKs de cliente e documentação automaticamente, service meshes (Istio, Linkerd) aplicam políticas de tráfego sem configuração adicional, e plataformas de observabilidade rastreiam requisições entre serviços usando cabeçalhos HTTP padrão.

#### 5. Versionamento e Evolução Mais Simples

APIs REST fazem versionamento via caminho de URL (`/v1/`, `/v2/`) ou cabeçalhos. Adicionar novos campos em uma resposta JSON não quebra clientes existentes, permitindo que o serviço evolua de forma independente — um princípio central dos microsserviços. Os contratos WSDL rígidos do SOAP tornam qualquer mudança de schema um potencial evento destrutivo que exige redistribuição do WSDL.

#### 6. Experiência do Desenvolvedor

APIs REST podem ser testadas com qualquer cliente HTTP (curl, Postman, navegador). Integrar um novo time ou consumidor de serviço não exige toolkit SOAP, parser de WSDL ou classes stub geradas — apenas um cliente HTTP e a referência da API.

#### 7. Comunicação Entre Serviços

Dentro de um mesh de microsserviços, os serviços se chamam com frequência. REST sobre HTTP/1.1 ou HTTP/2 se encaixa naturalmente nesse padrão e é suportado por todos os mecanismos de service discovery e balanceamento de carga. O SOAP adiciona fricção a cada salto na comunicação.

---

## Diferenças Principais

| Dimensão | SOAP (Legado) | REST (Novo) |
|---|---|---|
| Protocolo | SOAP sobre HTTP/HTTPS | HTTP/HTTPS |
| Formato de mensagem | XML (Envelope/Body/Header) | JSON |
| Contrato da API | WSDL | OpenAPI 3.0 (Swagger) |
| Operações | Ações no cabeçalho `SOAPAction` | Verbos HTTP + caminhos de recurso |
| Autenticação | WS-Security / Basic Auth | OAuth 2.0 / API Key (Bearer token) |
| Formato de erro | XML `<soap:Fault>` | JSON problem detail (RFC 7807) |
| Cache | Não suportado nativamente | Respostas `GET` cacheáveis via HTTP |
| Content-Type | `text/xml` | `application/json` |
| Ferramental | Geradores de WSDL, clientes SOAP | Geradores OpenAPI, qualquer cliente HTTP |

---

## Mudanças na Autenticação

### Legado (SOAP — WS-Security)

O SOAP usa WS-Security para embutir credenciais diretamente no envelope XML:

```xml
<soapenv:Header>
  <wsse:Security>
    <wsse:UsernameToken>
      <wsse:Username>seu-usuario</wsse:Username>
      <wsse:Password>sua-senha</wsse:Password>
    </wsse:UsernameToken>
  </wsse:Security>
</soapenv:Header>
```

### Novo (REST — Bearer Token)

O REST utiliza OAuth 2.0. O fluxo tem duas etapas: obter um token de acesso e, em seguida, enviá-lo como cabeçalho `Authorization` em todas as requisições.

```bash
# Passo 1: Obter o token
POST https://auth.[service-domain].com/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=SEU_CLIENT_ID&client_secret=SEU_SECRET

# Resposta
{
  "access_token": "eyJhbGciOiJSUzI1NiIsIn...",
  "token_type": "Bearer",
  "expires_in": 3600
}

# Passo 2: Usar o token em todas as chamadas da API
Authorization: Bearer eyJhbGciOiJSUzI1NiIsIn...
```

> **Atenção:** O token expira após `expires_in` segundos (geralmente 1 hora). Sua aplicação deve implementar lógica de renovação automática para evitar erros `401 Unauthorized`.

---

## Mapeamento de Métodos SOAP para Endpoints REST

Substitua cada operação SOAP pelo seu equivalente REST. Todos os caminhos são relativos à URL base.

> **Instrução para mantenedores do serviço:** Preencha a tabela abaixo com as operações reais expostas pelo `[Nome do Serviço]`. Adicione ou remova linhas conforme necessário.

| Operação SOAP | Cabeçalho SOAPAction | Método REST | Caminho REST | Observações |
|---|---|---|---|---|
| `GetCustomer` | `urn:GetCustomer` | `GET` | `/customers/{customerId}` | O parâmetro de caminho substitui o campo no corpo da requisição |
| `ListCustomers` | `urn:ListCustomers` | `GET` | `/customers?page=1&limit=50` | Filtragem via query params |
| `CreateCustomer` | `urn:CreateCustomer` | `POST` | `/customers` | Corpo: objeto JSON |
| `UpdateCustomer` | `urn:UpdateCustomer` | `PUT` | `/customers/{customerId}` | Substituição completa do recurso |
| `PatchCustomer` | `urn:PatchCustomer` | `PATCH` | `/customers/{customerId}` | Atualização parcial |
| `DeleteCustomer` | `urn:DeleteCustomer` | `DELETE` | `/customers/{customerId}` | Retorna `204 No Content` |
| `GetOrder` | `urn:GetOrder` | `GET` | `/orders/{orderId}` | |
| `ListOrdersByCustomer` | `urn:ListOrdersByCustomer` | `GET` | `/customers/{customerId}/orders` | Recurso aninhado |
| `CreateOrder` | `urn:CreateOrder` | `POST` | `/orders` | |
| `CancelOrder` | `urn:CancelOrder` | `POST` | `/orders/{orderId}/cancel` | Transição de estado como sub-recurso |
| `GetProduct` | `urn:GetProduct` | `GET` | `/products/{productId}` | |
| `SearchProducts` | `urn:SearchProducts` | `GET` | `/products?q={query}&category={cat}` | Busca full-text via query param |
| `[Adicione linhas conforme necessário]` | | | | |

---

## Referência de Migração do SDK

O SDK do `[Nome do Serviço]` foi atualizado para encapsular a API REST. A biblioteca cliente SOAP legada está descontinuada juntamente com o endpoint SOAP.

### Instalação

```bash
# Remover o cliente SOAP legado
npm uninstall @[org]/[service]-soap-client

# Instalar o novo SDK REST
npm install @[org]/[service]-sdk
```

> O SDK também está disponível para Python (`pip install [service]-sdk`), Java (Maven/Gradle) e Go (`go get [module-path]`).

---

### Exemplos Comparativos do SDK

#### Buscar um Cliente

**SOAP (legado)**

```javascript
const client = new SoapClient({ wsdl: 'https://[service]/service.wsdl' });

const result = await client.GetCustomerAsync({
  GetCustomerRequest: {
    CustomerId: '12345',
  },
});

const customer = result.GetCustomerResponse.Customer;
```

**SDK REST (novo)**

```javascript
import { ServiceClient } from '@[org]/[service]-sdk';

const client = new ServiceClient({ accessToken: process.env.SERVICE_TOKEN });

const customer = await client.customers.get('12345');
```

---

#### Criar um Cliente

**SOAP (legado)**

```xml
<soapenv:Body>
  <tns:CreateCustomerRequest>
    <tns:FirstName>Maria</tns:FirstName>
    <tns:LastName>Silva</tns:LastName>
    <tns:Email>maria.silva@exemplo.com.br</tns:Email>
  </tns:CreateCustomerRequest>
</soapenv:Body>
```

**SDK REST (novo)**

```javascript
const novoCliente = await client.customers.create({
  firstName: 'Maria',
  lastName: 'Silva',
  email: 'maria.silva@exemplo.com.br',
});

console.log(novoCliente.id); // '67890'
```

---

#### Listar Pedidos de um Cliente

**SOAP (legado)**

```javascript
const result = await client.ListOrdersByCustomerAsync({
  ListOrdersByCustomerRequest: {
    CustomerId: '12345',
    PageNumber: 1,
    PageSize: 50,
  },
});
```

**SDK REST (novo)**

```javascript
const pedidos = await client.customers.orders.list('12345', {
  page: 1,
  limit: 50,
});

// pedidos.data   — array de objetos de pedido
// pedidos.total  — contagem total
// pedidos.next   — cursor/URL para a próxima página
```

---

#### Cancelar um Pedido

**SOAP (legado)**

```javascript
await client.CancelOrderAsync({
  CancelOrderRequest: {
    OrderId: 'ORD-999',
    Reason: 'Solicitação do cliente',
  },
});
```

**SDK REST (novo)**

```javascript
await client.orders.cancel('ORD-999', { reason: 'Solicitação do cliente' });
```

---

#### HTTP Puro (sem SDK)

Se preferir chamar a API diretamente, sem o SDK:

```bash
# Buscar um cliente
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  https://api.[service-domain].com/v1/customers/12345

# Criar um cliente
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Maria","lastName":"Silva","email":"maria.silva@exemplo.com.br"}' \
  https://api.[service-domain].com/v1/customers
```

---

## Mudanças no Formato de Requisição e Resposta

### Transformação de Requisição

Requisições SOAP encapsulam os parâmetros em XML com um envelope tipado. Requisições REST usam um corpo JSON simples ou parâmetros de caminho/query na URL.

**Antes (corpo XML do SOAP)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:tns="http://[service-domain].com/schema">
  <soapenv:Body>
    <tns:GetOrderRequest>
      <tns:OrderId>ORD-999</tns:OrderId>
    </tns:GetOrderRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

**Depois (REST)**

```
GET /v1/orders/ORD-999
Authorization: Bearer <token>
```

### Transformação de Resposta

**Antes (resposta XML do SOAP)**

```xml
<soapenv:Envelope ...>
  <soapenv:Body>
    <tns:GetOrderResponse>
      <tns:Order>
        <tns:OrderId>ORD-999</tns:OrderId>
        <tns:Status>SHIPPED</tns:Status>
        <tns:Total>149.99</tns:Total>
      </tns:Order>
    </tns:GetOrderResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

**Depois (resposta JSON do REST)**

```json
{
  "id": "ORD-999",
  "status": "shipped",
  "total": 149.99,
  "currency": "BRL",
  "createdAt": "2026-03-15T10:23:00Z",
  "updatedAt": "2026-03-20T14:05:00Z"
}
```

### Convenções de Nomenclatura de Campos

Respostas SOAP frequentemente usavam `PascalCase`. A API REST usa `camelCase` em todos os campos. Atualize os acessos a campos no seu código conforme a tabela abaixo.

| Campo SOAP | Campo REST |
|---|---|
| `OrderId` | `id` |
| `CustomerId` | `customerId` |
| `FirstName` | `firstName` |
| `LastName` | `lastName` |
| `CreatedDate` | `createdAt` (formato ISO 8601) |
| `Status` | `status` (enum em letras minúsculas) |

---

## Tratamento de Erros

### Falhas SOAP (legado)

No SOAP, erros são retornados dentro de um elemento `<soap:Fault>` no corpo XML, independentemente do código de status HTTP:

```xml
<soapenv:Fault>
  <faultcode>tns:NotFound</faultcode>
  <faultstring>Cliente não encontrado</faultstring>
  <detail>
    <tns:ErrorCode>CUST_404</tns:ErrorCode>
  </detail>
</soapenv:Fault>
```

### Respostas de Erro REST (RFC 7807 Problem Detail)

No REST, o código de status HTTP já indica a categoria do erro. O corpo JSON fornece detalhes adicionais no formato padronizado RFC 7807:

```json
{
  "type": "https://api.[service-domain].com/errors/not-found",
  "title": "Recurso Não Encontrado",
  "status": 404,
  "detail": "Cliente com id '12345' não existe.",
  "instance": "/v1/customers/12345"
}
```

### Referência de Códigos de Status HTTP

| Status HTTP | Significado | Causa Comum |
|---|---|---|
| `200 OK` | Sucesso (leitura/atualização) | |
| `201 Created` | Recurso criado | POST bem-sucedido |
| `204 No Content` | Sucesso (sem corpo) | DELETE bem-sucedido |
| `400 Bad Request` | Entrada inválida | Campo obrigatório ausente |
| `401 Unauthorized` | Falha de autenticação | Token ausente ou expirado |
| `403 Forbidden` | Acesso negado | Permissões insuficientes |
| `404 Not Found` | Recurso inexistente | ID inválido |
| `409 Conflict` | Conflito de estado | Recurso duplicado |
| `422 Unprocessable Entity` | Erro de validação | Violação de regra de negócio |
| `429 Too Many Requests` | Rate limit atingido | Aguarde e tente novamente |
| `500 Internal Server Error` | Erro interno do servidor | Entre em contato com o suporte |

### Tratamento de Erros no SDK

```javascript
import { ServiceClient, NotFoundError, RateLimitError } from '@[org]/[service]-sdk';

try {
  const customer = await client.customers.get('12345');
} catch (err) {
  if (err instanceof NotFoundError) {
    console.error('Cliente não encontrado:', err.detail);
  } else if (err instanceof RateLimitError) {
    console.warn('Rate limit atingido. Tente novamente após:', err.retryAfter);
  } else {
    throw err;
  }
}
```

---

## Checklist de Migração

Use este checklist para acompanhar o progresso da sua equipe na migração.

### Preparação

- [ ] Revisar este guia e compartilhá-lo com todos os envolvidos
- [ ] Obter o `client_id` e o `client_secret` OAuth 2.0 junto à equipe do `[Nome do Serviço]`
- [ ] Atualizar a dependência do SDK: remover o cliente SOAP legado e instalar o novo SDK REST
- [ ] Configurar `BASE_URL` e `ACCESS_TOKEN` no ambiente ou cofre de segredos

### Mudanças no Código

- [ ] Substituir a inicialização do cliente SOAP pela inicialização do cliente REST SDK
- [ ] Atualizar a lógica de autenticação (WS-Security → Bearer token com renovação)
- [ ] Migrar cada chamada de operação SOAP usando a tabela de mapeamento acima
- [ ] Atualizar as referências de campos (`PascalCase` → `camelCase`)
- [ ] Substituir o tratamento de `<soap:Fault>` por código de status HTTP + tratamento de erro JSON
- [ ] Atualizar qualquer serialização/deserialização de XML para JSON

### Testes

- [ ] Executar testes de integração contra o ambiente sandbox da API REST
- [ ] Validar que todas as operações mapeadas retornam os formatos de dados esperados
- [ ] Testar cenários de erro (404, 401, 422) e confirmar que o tratamento funciona corretamente
- [ ] Realizar teste de carga para verificar se a performance atende ao SLA sob o tráfego esperado

### Cutover (Virada para Produção)

- [ ] Implantar o cliente baseado em REST em staging e validar
- [ ] Agendar a virada para produção antes de **[Data de Descontinuação]**
- [ ] Implantar em produção
- [ ] Monitorar taxas de erro e latência por 48 horas após a virada
- [ ] Confirmar o descomissionamento de toda infraestrutura específica de SOAP (parsers de WSDL, schemas XML)

---

## Suporte e Contatos

| Função | Contato |
|---|---|
| Responsável pela API | `[responsavel@service-domain.com]` |
| Suporte ao Desenvolvedor | `[suporte@service-domain.com]` |
| Referência da API | `https://docs.[service-domain].com/api/v1` |
| Especificação OpenAPI | `https://api.[service-domain].com/v1/openapi.json` |
| Ambiente Sandbox | `https://sandbox.api.[service-domain].com/v1` |
| Página de Status | `https://status.[service-domain].com` |
| Plantão de Migração | Quintas-feiras, 14h00–15h00 UTC — `[link do calendário]` |

---

*Este documento é mantido pela equipe de plataforma do `[Nome do Serviço]`. Para correções ou adições, abra uma issue em `[link-do-repositório]` ou entre em contato diretamente com o responsável pela API.*
