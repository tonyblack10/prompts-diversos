# Documento de Arquitetura de Software
# Central de Envio de SMS's

**Versão:** 1.0.0
**Data:** 2026-04-28
**Status:** Em elaboração
**Confidencialidade:** Interno

---

## Histórico de Revisões

| Versão | Data       | Autor                  | Descrição                         |
|--------|------------|------------------------|-----------------------------------|
| 1.0.0  | 2026-04-28 | Arquitetura de Software | Versão inicial do documento        |

---

## Sumário

1. [Visão Geral do Sistema](#1-visão-geral-do-sistema)
2. [Decisões Arquiteturais](#2-decisões-arquiteturais)
3. [Descrição dos Componentes](#3-descrição-dos-componentes)
4. [Diagrama de Componentes](#4-diagrama-de-componentes)
5. [Fluxos de Dados](#5-fluxos-de-dados)
6. [Modelo de Dados](#6-modelo-de-dados)
7. [Contratos da API REST](#7-contratos-da-api-rest)
8. [Guia de Integração — Client Java SDK](#8-guia-de-integração--client-java-sdk)
9. [Configurações e Variáveis de Ambiente](#9-configurações-e-variáveis-de-ambiente)
10. [Considerações de Segurança](#10-considerações-de-segurança)
11. [Considerações de Escalabilidade e Resiliência](#11-considerações-de-escalabilidade-e-resiliência)
12. [Glossário](#12-glossário)

---

## 1. Visão Geral do Sistema

### 1.1 Propósito e Escopo

A **Central de Envio de SMS's** é uma plataforma distribuída de mensageria móvel desenvolvida para centralizar, orquestrar e rastrear o envio de mensagens curtas (SMS) para destinatários finais, por meio de integração com provedores externos de telecomunicação.

O sistema atua como intermediário confiável entre sistemas corporativos internos (ou parceiros externos) e os provedores de envio de SMS, abstraindo a complexidade de integração, gerenciamento de filas, retentativas e rastreamento de status de entrega.

O escopo da Central abrange:

- Recebimento de requisições de envio de SMS oriundas de sistemas externos via API REST ou SDK cliente;
- Validação, enfileiramento e processamento assíncrono das requisições de envio;
- Integração com provedores externos de SMS para o envio efetivo das mensagens;
- Recebimento e persistência de confirmações de entrega (delivery receipts) via AWS SQS e Webhook HTTP;
- Disponibilização de SDK em Java para facilitar a integração de sistemas clientes.

### 1.2 Objetivos de Qualidade

| Atributo de Qualidade | Meta                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------|
| **Disponibilidade**   | SLA de 99,9% para a API REST; degradação graciosa em caso de falha parcial dos componentes |
| **Escalabilidade**    | Escalonamento horizontal do worker para absorção de picos de demanda                       |
| **Resiliência**       | Reprocessamento automático via DLQ; circuit breaker para chamadas ao provider              |
| **Rastreabilidade**   | Correlação de cada mensagem enviada com seu status de entrega via identificador único       |
| **Segurança**         | Autenticação por API Key; políticas IAM mínimas para acesso a recursos AWS                 |
| **Manutenibilidade**  | Separação clara de responsabilidades por microsserviço; baixo acoplamento entre módulos    |

### 1.3 Público-Alvo do Documento

Este documento destina-se aos seguintes perfis:

- **Arquitetos de Software**: para validação das decisões arquiteturais e evolução da plataforma;
- **Engenheiros de Software Back-end**: para implementação, manutenção e extensão dos microsserviços;
- **Engenheiros de DevOps/SRE**: para provisionamento de infraestrutura, monitoramento e operação;
- **Tech Leads e Gerentes de Engenharia**: para visão executiva da solução técnica e suas implicações.

---

## 2. Decisões Arquiteturais

### 2.1 Justificativa para Arquitetura de Microsserviços

A Central de Envio de SMS's adota o padrão de **arquitetura de microsserviços** em detrimento de uma aplicação monolítica pelas seguintes razões:

**Isolamento de falhas**: Uma falha no componente `callback` — por exemplo, indisponibilidade do banco de dados — não compromete o funcionamento do `api-rest` ou do `worker`. Cada serviço tem seu ciclo de vida independente.

**Escalabilidade granular**: O componente `worker`, responsável pelo processamento intensivo de envio, pode ser escalado horizontalmente de forma independente, sem impacto nos demais serviços. Em períodos de baixa demanda, apenas as réplicas necessárias são mantidas ativas.

**Implantação independente**: Cada microsserviço possui seu próprio pipeline de build, testes e deploy, reduzindo o risco de regressão e acelerando o ciclo de entrega de novas funcionalidades.

**Separação de responsabilidades**: A divisão em serviços distintos (`api-rest`, `worker`, `callback`) reflete as fronteiras de negócio e técnicas do sistema, tornando o código mais coeso e compreensível.

**Tecnologias especializadas**: Embora todos os serviços utilizem Java + Spring, a arquitetura permite que serviços futuros adotem tecnologias distintas conforme necessário, sem afetar os demais.

### 2.2 Justificativa para Mensageria Assíncrona (RabbitMQ e AWS SNS/SQS)

A adoção de mensageria assíncrona é decisão central da arquitetura, motivada por:

**Desacoplamento temporal**: O `api-rest` não precisa aguardar a conclusão do envio pelo provider para retornar resposta ao cliente. A requisição é enfileirada e processada de forma assíncrona, reduzindo a latência percebida pelo sistema cliente.

**Absorção de picos de carga**: A fila RabbitMQ atua como buffer entre a entrada de requisições e o processamento, evitando sobrecarga no provider em momentos de alta demanda.

**RabbitMQ** é utilizado para a comunicação interna entre `api-rest` e `worker` por oferecer controle fino de roteamento de mensagens (exchanges, bindings), suporte nativo a DLQ e maturidade comprovada em ambientes corporativos Java/Spring via Spring AMQP.

**AWS SNS + SQS** é adotado para o fluxo de retorno de confirmações de entrega por ser o mecanismo nativo do provider externo para notificação de status, e por oferecer entrega garantida, fanout para múltiplos consumidores via SNS, e retenção configurável via SQS.

### 2.3 Justificativa para Separação entre Worker e Callback

A separação dos componentes `worker` e `callback` em serviços distintos é fundamentada em:

**Responsabilidades opostas e independentes**: O `worker` é um produtor de envios — consome da fila interna e chama o provider. O `callback` é um consumidor de resultados — recebe notificações externas e persiste status. Suas cargas de trabalho, ciclos de vida e dependências são completamente distintos.

**Escalabilidade independente**: Em cenários de alto volume, é possível escalar o `worker` sem impacto no `callback`, e vice-versa — por exemplo, quando há acúmulo de confirmações de entrega oriundas de campanhas anteriores.

**Isolamento de falhas de persistência**: Uma falha no banco de dados utilizado pelo `callback` não bloqueia o `worker` de continuar processando envios.

**Clareza de fronteiras**: A separação evita que um único serviço acumule responsabilidades de envio, integração com provider, consumo de SQS e persistência, tornando cada componente mais coeso e testável.

### 2.4 Padrões Adotados

| Padrão                        | Componente(s)              | Descrição                                                                                                 |
|-------------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------|
| **Producer/Consumer**         | api-rest → RabbitMQ → worker | Publicação e consumo assíncrono de mensagens via broker                                                  |
| **Dead Letter Queue (DLQ)**   | worker, callback           | Mensagens que excedem tentativas de reprocessamento são enviadas a uma fila de erro para análise manual   |
| **Webhook**                   | provider → callback        | O provider notifica o callback diretamente via HTTP POST para confirmações de entrega em tempo real       |
| **SDK Client**                | client-java                | Abstração da integração via biblioteca distribuída como artefato Maven, simplificando o uso da API        |
| **Circuit Breaker**           | worker                     | Interrupção temporária de chamadas ao provider em caso de falhas consecutivas, evitando sobrecarga        |
| **Idempotência**              | callback                   | Garantia de que o processamento duplicado de uma notificação não gera registros duplicados no banco       |
| **Correlation ID**            | todos os serviços          | Identificador único propagado em todos os saltos para rastreabilidade fim-a-fim                           |

---

## 3. Descrição dos Componentes

### 3.1 api-rest

**Responsabilidade única:** Receber, validar e enfileirar requisições de envio de SMS oriundas de sistemas externos.

#### Tecnologias e Frameworks

| Tecnologia          | Versão recomendada | Finalidade                                      |
|---------------------|--------------------|-------------------------------------------------|
| Java                | 21 (LTS)           | Linguagem principal                             |
| Spring Boot         | 3.x                | Framework de aplicação                          |
| Spring Web MVC      | 3.x                | Exposição de endpoints REST                     |
| Spring AMQP         | 3.x                | Publicação de mensagens no RabbitMQ             |
| Spring Validation   | 3.x                | Validação de request bodies (Bean Validation)   |
| Micrometer + Actuator | —               | Métricas, health checks e observabilidade       |

#### Interfaces Expostas

| Tipo       | Detalhe                            | Descrição                                     |
|------------|------------------------------------|-----------------------------------------------|
| REST POST  | `POST /sms/send`                   | Recebe requisição de envio de SMS              |
| REST GET   | `GET /sms/{messageId}/status`      | Consulta status de uma mensagem               |
| REST GET   | `GET /actuator/health`             | Health check do serviço                       |

#### Interfaces Consumidas

| Tipo   | Detalhe                       | Descrição                                     |
|--------|-------------------------------|-----------------------------------------------|
| AMQP   | Exchange: `sms.exchange`      | Publica mensagens para processamento pelo worker |

#### Dependências Diretas

- **RabbitMQ**: publicação de mensagens na fila `sms.queue`
- **Nenhuma dependência direta** com `worker`, `callback` ou `provider`

#### Estratégias de Resiliência

| Estratégia          | Implementação                                                                                  |
|---------------------|-----------------------------------------------------------------------------------------------|
| **Validação de entrada** | Bean Validation (@NotNull, @Size, @Pattern) para rejeitar requisições malformadas antes do enfileiramento |
| **Retry na publicação** | Spring Retry com backoff exponencial para falhas transitórias de publicação no RabbitMQ     |
| **Correlation ID**  | Geração de UUID único por requisição, propagado no header da mensagem AMQP e na resposta HTTP |

---

### 3.2 worker

**Responsabilidade única:** Consumir mensagens de envio da fila RabbitMQ, chamar o provider externo para efetuar o envio do SMS e publicar eventos de resultado no AWS SNS.

#### Tecnologias e Frameworks

| Tecnologia             | Versão recomendada | Finalidade                                           |
|------------------------|--------------------|------------------------------------------------------|
| Java                   | 21 (LTS)           | Linguagem principal                                  |
| Spring Boot            | 3.x                | Framework de aplicação                               |
| Spring AMQP            | 3.x                | Consumo de mensagens do RabbitMQ                     |
| Spring Cloud AWS SQS/SNS | 3.x            | Publicação de eventos no AWS SNS                     |
| Spring WebFlux / WebClient | 3.x          | Chamadas HTTP reativas ao provider externo           |
| Resilience4j           | 2.x                | Circuit breaker, retry e rate limiter                |
| Micrometer + Actuator  | —                  | Métricas e health checks                             |

#### Interfaces Consumidas

| Tipo   | Detalhe                        | Descrição                                              |
|--------|--------------------------------|--------------------------------------------------------|
| AMQP   | Fila: `sms.queue`              | Consome mensagens de envio publicadas pelo api-rest    |
| HTTP   | API REST do provider           | Chama o endpoint de envio de SMS do provider externo   |

#### Interfaces Expostas (publicações)

| Tipo        | Detalhe                       | Descrição                                              |
|-------------|-------------------------------|--------------------------------------------------------|
| SNS Publish | Tópico: `sms-delivery-topic`  | Publica eventos de resultado do envio para o callback  |
| AMQP        | DLQ: `sms.queue.dlq`         | Mensagens com falha são encaminhadas à DLQ             |

#### Dependências Diretas

- **RabbitMQ**: consumo da fila `sms.queue`
- **provider**: chamada HTTP REST para envio do SMS
- **AWS SNS**: publicação de eventos de resultado

#### Estratégias de Resiliência

| Estratégia          | Implementação                                                                                                    |
|---------------------|------------------------------------------------------------------------------------------------------------------|
| **Circuit Breaker** | Resilience4j CircuitBreaker nas chamadas HTTP ao provider: abre após N falhas consecutivas, reset após timeout   |
| **Retry com backoff** | Resilience4j Retry com backoff exponencial para erros transitórios do provider (5xx, timeout)                 |
| **DLQ**             | Mensagens que esgotam todas as tentativas são encaminhadas à `sms.queue.dlq` para análise e reprocessamento manual |
| **Acknowledge manual** | ACK da mensagem AMQP ocorre somente após processamento bem-sucedido; NACK em caso de falha enfileira na DLQ  |
| **Idempotência no envio** | Uso do `correlationId` como chave de idempotência na chamada ao provider, quando suportado              |

---

### 3.3 callback

**Responsabilidade única:** Receber notificações de status de entrega do provider (via AWS SQS e via Webhook HTTP), persistir os registros no banco de dados relacional e garantir idempotência no processamento.

#### Tecnologias e Frameworks

| Tecnologia             | Versão recomendada | Finalidade                                                   |
|------------------------|--------------------|--------------------------------------------------------------|
| Java                   | 21 (LTS)           | Linguagem principal                                          |
| Spring Boot            | 3.x                | Framework de aplicação                                       |
| Spring Cloud AWS SQS   | 3.x                | Consumo de notificações da fila AWS SQS                      |
| Spring Web MVC         | 3.x                | Exposição do endpoint de Webhook HTTP                        |
| Spring Data JPA        | 3.x                | Persistência no banco de dados relacional                    |
| Hibernate              | 6.x                | ORM para mapeamento de entidades                             |
| Flyway                 | —                  | Versionamento e migração do schema do banco de dados         |
| Micrometer + Actuator  | —                  | Métricas e health checks                                     |

#### Interfaces Expostas

| Tipo      | Detalhe                           | Descrição                                                  |
|-----------|-----------------------------------|------------------------------------------------------------|
| REST POST | `POST /callback/webhook`          | Endpoint para recebimento de notificações via Webhook HTTP |
| REST GET  | `GET /actuator/health`            | Health check do serviço                                    |

#### Interfaces Consumidas

| Tipo        | Detalhe                        | Descrição                                                    |
|-------------|--------------------------------|--------------------------------------------------------------|
| SQS Consume | Fila: `sms-delivery-queue`     | Consome notificações de entrega via AWS SQS                  |
| JDBC        | Banco de dados relacional      | Persiste status de entrega das mensagens                     |

#### Dependências Diretas

- **AWS SQS**: consumo da fila `sms-delivery-queue`
- **Banco de dados relacional (PostgreSQL)**: persistência dos status de entrega

#### Estratégias de Resiliência

| Estratégia                  | Implementação                                                                                               |
|-----------------------------|-------------------------------------------------------------------------------------------------------------|
| **Idempotência**            | Verificação do `messageId` no banco antes de inserir; registro ignorado se já existir (UPSERT ou SELECT-first) |
| **DLQ no SQS**              | Mensagens que falham N vezes no processamento são enviadas para `sms-delivery-queue-dlq`                    |
| **Transação no webhook**    | O endpoint de webhook responde HTTP 200 somente após commit bem-sucedido no banco; retorna 5xx em caso de falha para induzir reenvio |
| **Retry automático SQS**    | O Spring Cloud AWS reencaminha a mensagem automaticamente para reprocessamento em caso de exceção não tratada |

---

### 3.4 provider

**Responsabilidade única:** Efetuar o envio das mensagens SMS às operadoras de telefonia e notificar o sistema sobre o status de entrega.

> **Nota:** O `provider` é um serviço externo de terceiros, não pertencente ao escopo de desenvolvimento da Central. Sua descrição neste documento serve para fins de integração e contrato.

#### Interfaces Expostas (consumidas pelo worker)

| Tipo     | Detalhe                    | Descrição                                              |
|----------|----------------------------|--------------------------------------------------------|
| REST POST | `POST /v1/messages/send`  | Envia uma mensagem SMS para um número destinatário     |
| REST GET  | `GET /v1/messages/{id}`   | Consulta o status de uma mensagem enviada              |

#### Mecanismos de Notificação de Entrega

| Mecanismo     | Detalhe                                                                           |
|---------------|-----------------------------------------------------------------------------------|
| **AWS SNS**   | O provider publica notificações de entrega em um tópico SNS compartilhado        |
| **Webhook**   | O provider realiza chamada HTTP POST para o endpoint configurado no callback      |

#### Dependências Relevantes para a Central

- **worker** → provider: chamada HTTP POST para envio de SMS
- **provider** → callback: notificação via SNS/SQS e/ou Webhook

---

### 3.5 client-java

**Responsabilidade única:** Fornecer uma biblioteca Java (SDK) que abstraia a integração de sistemas externos com a `api-rest` da Central, simplificando autenticação, serialização, retry e tratamento de erros.

#### Tecnologias e Frameworks

| Tecnologia          | Versão recomendada | Finalidade                                         |
|---------------------|--------------------|----------------------------------------------------|
| Java                | 11+ (compatível)   | Linguagem do SDK                                   |
| Spring Web / WebClient | opcional       | Implementação do cliente HTTP (ou Java HttpClient) |
| Jackson             | 2.x                | Serialização/deserialização JSON                   |
| Maven / Gradle      | —                  | Distribuição como artefato em repositório          |

#### Interfaces Expostas (para sistemas clientes)

| Tipo           | Detalhe                               | Descrição                                               |
|----------------|---------------------------------------|---------------------------------------------------------|
| API Java       | `SmsClient#send(SendSmsRequest)`      | Envia uma requisição de SMS para a Central              |
| API Java       | `SmsClient#getStatus(String messageId)` | Consulta o status de uma mensagem enviada             |

#### Dependências Diretas

- **api-rest**: realiza chamadas HTTP REST aos endpoints da `api-rest`

#### Estratégias de Resiliência

| Estratégia          | Implementação                                                                               |
|---------------------|---------------------------------------------------------------------------------------------|
| **Retry automático** | Retry configurável por instância do cliente para erros transitórios (5xx, timeout)          |
| **Timeout configurável** | Timeouts de conexão e leitura configuráveis pelo sistema cliente                       |
| **Tratamento de erros** | Exceções tipadas (`SmsClientException`, `SmsValidationException`) para facilitar o tratamento |

---

## 4. Diagrama de Componentes

### 4.1 Diagrama Principal

```
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║                              SISTEMAS EXTERNOS (CLIENTES)                                    ║
║                                                                                              ║
║   ┌──────────────────────────────────────────────────────────┐                              ║
║   │                    Sistema Cliente                        │                              ║
║   │           (qualquer aplicação Java corporativa)          │                              ║
║   │                                                          │                              ║
║   │    ┌────────────────────────────────────────────┐        │                              ║
║   │    │              client-java (SDK)              │        │                              ║
║   │    │         [biblioteca JAR distribuída]        │        │                              ║
║   │    └──────────────────────┬─────────────────────┘        │                              ║
║   └─────────────────────────-─┼─────────────────────────────-┘                              ║
╚═════════════════════════════-─┼─────────────────────────────-═══════════════════════════════╝
                                │
                                │ HTTP/REST (POST /sms/send)
                                │ [API Key no header]
                                ▼
╔══════════════════════════════════════════════════════════════════════════════════════════════╗
║                           CENTRAL DE ENVIO DE SMS's — DOMÍNIO INTERNO                        ║
║                                                                                              ║
║   ┌──────────────────────────────────────────────┐                                          ║
║   │                   api-rest                    │                                          ║
║   │         [Spring Boot + Spring Web MVC]        │                                          ║
║   │                                               │                                          ║
║   │  • Valida requisição (Bean Validation)        │                                          ║
║   │  • Gera correlationId (UUID)                  │                                          ║
║   │  • Publica mensagem na fila RabbitMQ          │                                          ║
║   └──────────────────────┬────────────────────────┘                                          ║
║                           │                                                                  ║
║                           │ AMQP (Publish)                                                   ║
║                           │ Exchange: sms.exchange                                           ║
║                           ▼                                                                  ║
║   ┌──────────────────────────────────────────────┐                                          ║
║   │                  RabbitMQ                     │                                          ║
║   │         [Broker de Mensagens Interno]         │                                          ║
║   │                                               │                                          ║
║   │  Fila: sms.queue  ──────── DLQ: sms.queue.dlq│                                          ║
║   └──────────────────────┬────────────────────────┘                                          ║
║                           │                                                                  ║
║                           │ AMQP (Consume)                                                   ║
║                           ▼                                                                  ║
║   ┌──────────────────────────────────────────────┐                                          ║
║   │                   worker                      │                                          ║
║   │  [Spring Boot + Spring AMQP + Resilience4j]  │                                          ║
║   │                                               │                                          ║
║   │  • Processa mensagem da fila                  │                                          ║
║   │  • Circuit Breaker / Retry (Resilience4j)     │                                          ║
║   │  • Chama API do provider (HTTP)               │                                          ║
║   │  • Publica evento de resultado no AWS SNS     │                                          ║
║   └────────────┬────────────────────┬─────────────┘                                          ║
║                │                    │                                                        ║
║                │ HTTP/REST          │ SNS Publish                                            ║
║                │ (envio de SMS)     │ Tópico: sms-delivery-topic                             ║
║                ▼                    ▼                                                        ║
╚═══════════════ │ ═════════════════ │ ════════════════════════════════════════════════════════╝
                 │                   │
╔══════════════  │  ═══════════════  │  ═════════════════════════════════════════════════════════╗
║  COMPONENTES EXTERNOS / CLOUD                                                                  ║
║                │                   │                                                           ║
║   ┌────────────┴───────────────┐   │ SNS Fanout                                               ║
║   │          provider          │   │                                                           ║
║   │  [Serviço externo de SMS]  │   ▼                                                           ║
║   │                            │  ┌───────────────────────────────────────┐                   ║
║   │  • Envia SMS às operadoras │  │              AWS SNS                   │                   ║
║   │  • Notifica status via SNS │  │    Tópico: sms-delivery-topic         │                   ║
║   │  • Notifica via Webhook    │  └────────────────────┬──────────────────┘                   ║
║   └────────────┬───────────────┘                       │                                       ║
║                │                                        │ SQS Subscribe                        ║
║                │ Webhook (HTTP POST)                    ▼                                       ║
║                │ /callback/webhook              ┌───────────────────────────────────────┐      ║
║                │                                │              AWS SQS                   │      ║
║                │                                │  Fila: sms-delivery-queue             │      ║
║                │                                │  DLQ:  sms-delivery-queue-dlq         │      ║
║                │                                └────────────────────┬──────────────────┘      ║
╚═══════════════ │ ══════════════════════════════════════ │ ═══════════════════════════════════════╝
                 │                                         │
╔═══════════════ │ ══════════════════════════════════════ │ ════════════════════════════════════════╗
║                          CENTRAL DE ENVIO DE SMS's — DOMÍNIO INTERNO                             ║
║                │                                         │                                        ║
║                │ HTTP POST                               │ SQS Consume                            ║
║                └─────────────────────┬───────────────────┘                                        ║
║                                      ▼                                                            ║
║   ┌────────────────────────────────────────────────────────────────────┐                          ║
║   │                          callback                                   │                          ║
║   │         [Spring Boot + Spring Cloud AWS + Spring Data JPA]         │                          ║
║   │                                                                     │                          ║
║   │  • Consome notificações via SQS (polling)                          │                          ║
║   │  • Recebe notificações via Webhook HTTP                            │                          ║
║   │  • Garante idempotência (verifica messageId no banco)              │                          ║
║   │  • Persiste status de entrega                                      │                          ║
║   └─────────────────────────────────────┬──────────────────────────────┘                          ║
║                                          │                                                         ║
║                                          │ JDBC                                                    ║
║                                          ▼                                                         ║
║   ┌────────────────────────────────────────────────────────────────────┐                          ║
║   │                    PostgreSQL (Banco de Dados)                       │                          ║
║   │                                                                     │                          ║
║   │  Tabela: sms_delivery_notifications                                 │                          ║
║   └─────────────────────────────────────────────────────────────────────┘                          ║
╚════════════════════════════════════════════════════════════════════════════════════════════════════╝
```

### 4.2 Legenda

| Símbolo / Notação        | Significado                                                          |
|--------------------------|----------------------------------------------------------------------|
| `──►`                    | Fluxo de comunicação unidirecional                                   |
| `HTTP/REST`              | Comunicação síncrona via protocolo HTTP com payload JSON             |
| `AMQP (Publish/Consume)` | Comunicação assíncrona via Advanced Message Queuing Protocol         |
| `SNS Publish`            | Publicação de evento no tópico AWS Simple Notification Service       |
| `SQS Consume`            | Consumo de mensagem da fila AWS Simple Queue Service                 |
| `JDBC`                   | Comunicação com banco de dados relacional via Java Database Connectivity |
| `Webhook (HTTP POST)`    | Chamada HTTP de entrada iniciada pelo provider externo               |
| `DLQ`                    | Dead Letter Queue — fila para mensagens que falharam no processamento |
| `[...]`                  | Tecnologias principais utilizadas no componente                      |

---

## 5. Fluxos de Dados

### 5.1 Fluxo de Envio de SMS (Caminho Feliz)

Este fluxo descreve o percurso de uma requisição de envio desde o sistema cliente até a confirmação do envio ao provider.

```
Passo  Origem          Destino         Mecanismo         Descrição
─────  ──────────────  ──────────────  ────────────────  ──────────────────────────────────────────────────────
  1    Sistema Cliente  client-java     Chamada Java       O sistema cliente invoca SmsClient#send(request)
  2    client-java      api-rest        HTTP POST          Chamada REST autenticada com API Key no header
                                                          Payload: { "to": "+5511...", "message": "..." }
  3    api-rest         —               Validação interna  Bean Validation valida campos obrigatórios, formato
                                                          do número, tamanho da mensagem
  4    api-rest         —               Lógica interna     Gera correlationId (UUID) único para a mensagem
  5    api-rest         RabbitMQ        AMQP Publish       Publica mensagem na Exchange sms.exchange
                                                          com correlationId no header AMQP
  6    api-rest         client-java     HTTP 202           Retorna ao cliente: { "messageId": "<uuid>",
                                                          "status": "QUEUED" }
  7    RabbitMQ         worker          AMQP Consume       Worker consome a mensagem da fila sms.queue
  8    worker           —               Lógica interna     Desserializa payload; valida estado interno
  9    worker           provider        HTTP POST          Chama a API do provider com os dados do SMS
                                                          e correlationId como chave de idempotência
 10    provider         worker          HTTP 200/201       Provider confirma o recebimento e retorna
                                                          messageId interno do provider
 11    worker           —               AMQP ACK           Worker faz ACK da mensagem no RabbitMQ
 12    worker           AWS SNS         SNS Publish        Publica evento de envio confirmado no tópico
                                                          sms-delivery-topic com status SENT
```

**Pré-condições:** Sistema cliente configurado com API Key válida; RabbitMQ e provider disponíveis.
**Pós-condições:** Mensagem na fila do provider para envio à operadora; evento publicado no SNS.

---

### 5.2 Fluxo de Confirmação de Entrega via SQS

Este fluxo descreve como o status de entrega do SMS retorna ao sistema após o provider notificar via AWS SNS.

```
Passo  Origem              Destino              Mecanismo         Descrição
─────  ──────────────────  ───────────────────  ────────────────  ──────────────────────────────────────────────────────
  1    Operadora Telefônica provider            Rede SMS          Operadora confirma ou nega a entrega do SMS
  2    provider             AWS SNS             SNS Publish       Provider publica notificação de entrega no tópico
                                                                  sms-delivery-topic com messageId e status
  3    AWS SNS              AWS SQS             SQS Subscribe     SNS faz fanout para a fila sms-delivery-queue
                                                                  (assinatura configurada na infraestrutura)
  4    AWS SQS              callback            SQS Consume       callback faz polling e consome a notificação
  5    callback             —                   Lógica interna    Extrai messageId e status da notificação
  6    callback             PostgreSQL           JDBC SELECT       Verifica se notificação com messageId já existe
                                                                  (checagem de idempotência)
  7a   [se existir]         callback            —                 Descarta a notificação (duplicata); faz ACK no SQS
  7b   [se não existir]     PostgreSQL          JDBC INSERT       Persiste registro em sms_delivery_notifications
  8    callback             AWS SQS             SQS ACK (delete)  Confirma o processamento removendo a mensagem da fila
```

**Tratamento de erro:** Se o INSERT falhar (ex: banco indisponível), o SQS não recebe ACK e reencaminha a mensagem. Após N tentativas, a mensagem é movida para `sms-delivery-queue-dlq`.

---

### 5.3 Fluxo de Confirmação de Entrega via Webhook

Este fluxo descreve como o callback recebe notificações diretas do provider via HTTP.

```
Passo  Origem     Destino      Mecanismo         Descrição
─────  ─────────  ───────────  ────────────────  ──────────────────────────────────────────────────────
  1    provider   callback     HTTP POST          Provider chama POST /callback/webhook com payload
                                                  contendo messageId, status e timestamp
  2    callback   —            Validação interna  Valida autenticidade da chamada (assinatura HMAC ou
                                                  token secreto no header)
  3    callback   —            Lógica interna     Extrai messageId e status do payload
  4    callback   PostgreSQL   JDBC SELECT        Verifica idempotência: messageId já registrado?
  5a   [existir]  callback     —                  Descarta; retorna HTTP 200 imediatamente
  5b   [não existir] PostgreSQL JDBC INSERT       Persiste registro em sms_delivery_notifications
  6    callback   provider     HTTP 200           Retorna 200 OK para confirmar recebimento
                                                  (em caso de falha retorna 5xx para reenvio)
```

**Observação:** A resposta HTTP deve ocorrer dentro do timeout configurado no provider (tipicamente 5–10 segundos). O processamento pesado deve ser desacoplado com filas internas se necessário.

---

### 5.4 Fluxo de Tratamento de Falhas

#### 5.4.1 Falha na Chamada ao Provider (worker)

```
Passo  Evento                           Ação
─────  ──────────────────────────────   ────────────────────────────────────────────────────────────────
  1    Chamada HTTP ao provider falha   Resilience4j Retry inicia backoff exponencial
                                        Tentativas: 1ª imediata, 2ª após 1s, 3ª após 2s, 4ª após 4s
  2    Todas as tentativas falham       CircuitBreaker registra falha; contador de falhas incrementado
  3    Threshold de falhas atingido     CircuitBreaker abre (OPEN): novas chamadas bloqueadas por X segundos
  4    CircuitBreaker em estado OPEN    Worker encaminha mensagem para DLQ: sms.queue.dlq via NACK/AMQP
  5    Timeout do CircuitBreaker        CircuitBreaker entra em HALF-OPEN: permite uma chamada de teste
  6    Chamada de teste bem-sucedida    CircuitBreaker fecha (CLOSED): processamento normal retomado
  7    DLQ monitorada                   Equipe de operação analisa mensagens na DLQ e decide reprocessamento
```

#### 5.4.2 Falha no Processamento da Notificação (callback via SQS)

```
Passo  Evento                               Ação
─────  ──────────────────────────────────   ──────────────────────────────────────────────────────────
  1    Exceção durante processamento SQS     Spring Cloud AWS não faz DELETE na mensagem (NACK implícito)
  2    SQS reencaminha após Visibility Timeout  Mensagem retorna à fila para nova tentativa
  3    N tentativas esgotadas               SQS encaminha mensagem para sms-delivery-queue-dlq
  4    DLQ monitorada via CloudWatch        Alarme dispara; equipe de operação é notificada
  5    Análise e reprocessamento manual     Mensagem na DLQ pode ser inspecionada e reencaminhada
```

#### 5.4.3 Correlação de Mensagens com Falha

Cada mensagem carrega um `correlationId` (UUID) propagado em todos os saltos. Em caso de falha, os logs de todos os serviços podem ser correlacionados por esse identificador para diagnóstico fim-a-fim.

---

## 6. Modelo de Dados

### 6.1 Entidade: SmsDeliveryNotification

Representa o registro de uma notificação de status de entrega de SMS persistida pelo componente `callback`.

| Atributo            | Tipo                       | Obrigatório | Descrição                                                                 |
|---------------------|----------------------------|-------------|---------------------------------------------------------------------------|
| `id`                | `BIGSERIAL` / `BIGINT`     | Sim         | Chave primária auto-incrementada (surrogate key)                          |
| `message_id`        | `VARCHAR(64)`              | Sim         | Identificador único da mensagem (correlationId gerado pelo api-rest)      |
| `provider_message_id` | `VARCHAR(128)`           | Não         | Identificador da mensagem no sistema do provider externo                  |
| `phone_number`      | `VARCHAR(20)`              | Sim         | Número de telefone destinatário no formato E.164 (ex: +5511999999999)     |
| `status`            | `VARCHAR(32)`              | Sim         | Status de entrega: SENT, DELIVERED, FAILED, UNDELIVERED, UNKNOWN         |
| `status_code`       | `VARCHAR(16)`              | Não         | Código de status retornado pelo provider ou operadora                     |
| `error_message`     | `TEXT`                     | Não         | Descrição do erro em caso de falha na entrega                             |
| `source`            | `VARCHAR(16)`              | Sim         | Origem da notificação: SQS ou WEBHOOK                                     |
| `received_at`       | `TIMESTAMP WITH TIME ZONE` | Sim         | Data/hora de recebimento da notificação pelo callback                     |
| `delivered_at`      | `TIMESTAMP WITH TIME ZONE` | Não         | Data/hora de entrega confirmada pela operadora (quando disponível)        |
| `created_at`        | `TIMESTAMP WITH TIME ZONE` | Sim         | Data/hora de criação do registro no banco                                 |
| `updated_at`        | `TIMESTAMP WITH TIME ZONE` | Sim         | Data/hora da última atualização do registro                               |

**Índices recomendados:**

| Índice                                   | Tipo   | Finalidade                                                  |
|------------------------------------------|--------|-------------------------------------------------------------|
| `PK: id`                                 | Primário | Identificação interna do registro                         |
| `UQ: message_id`                         | Único  | Garantia de idempotência — impede registros duplicados      |
| `IDX: phone_number`                      | B-Tree | Consultas por número de telefone                            |
| `IDX: status, created_at`               | B-Tree | Consultas de relatório por status e período                 |

### 6.2 Enum: DeliveryStatus

| Valor         | Descrição                                                    |
|---------------|--------------------------------------------------------------|
| `QUEUED`      | Mensagem recebida e enfileirada pela Central                 |
| `SENT`        | Mensagem enviada com sucesso ao provider                     |
| `DELIVERED`   | Confirmação de entrega recebida da operadora                 |
| `FAILED`      | Falha no envio (não chegou ao provider)                      |
| `UNDELIVERED` | Mensagem chegou ao provider mas não foi entregue ao destino  |
| `UNKNOWN`     | Status desconhecido ou notificação sem status reconhecível   |

---

### 6.3 Script DDL da Tabela

<!-- SCRIPT DA TABELA DE NOTIFICAÇÕES DE ENTREGA -->
<!-- Inserir aqui o script DDL da tabela de armazenamento das notificações -->

```sql
-- [A PREENCHER PELA EQUIPE DE DESENVOLVIMENTO]
-- Script de criação da tabela de notificações de entrega de SMS
-- Banco de dados: PostgreSQL 14+
-- Migração: Flyway — arquivo V1__create_sms_delivery_notifications.sql
--
-- Orientações para preenchimento:
--   1. Adicionar constraints de CHECK para o campo status (valores do enum DeliveryStatus)
--   2. Adicionar trigger de atualização automática do campo updated_at
--   3. Considerar particionamento por range de created_at para volumes > 100M registros
--   4. Avaliar uso de UNLOGGED TABLE em ambiente de staging para performance em testes de carga

-- Exemplo de estrutura (ajustar conforme revisão da equipe):
-- CREATE TABLE sms_delivery_notifications (
--     id                  BIGSERIAL PRIMARY KEY,
--     message_id          VARCHAR(64)              NOT NULL,
--     provider_message_id VARCHAR(128),
--     phone_number        VARCHAR(20)              NOT NULL,
--     status              VARCHAR(32)              NOT NULL,
--     status_code         VARCHAR(16),
--     error_message       TEXT,
--     source              VARCHAR(16)              NOT NULL,
--     received_at         TIMESTAMP WITH TIME ZONE NOT NULL,
--     delivered_at        TIMESTAMP WITH TIME ZONE,
--     created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
--     updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
--     CONSTRAINT uq_message_id UNIQUE (message_id),
--     CONSTRAINT chk_status CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED','UNDELIVERED','UNKNOWN')),
--     CONSTRAINT chk_source CHECK (source IN ('SQS','WEBHOOK'))
-- );
-- CREATE INDEX idx_sms_phone_number ON sms_delivery_notifications (phone_number);
-- CREATE INDEX idx_sms_status_created ON sms_delivery_notifications (status, created_at DESC);
```

---

## 7. Contratos da API REST

### 7.1 Headers Obrigatórios (todos os endpoints)

| Header            | Tipo     | Descrição                                               | Exemplo                          |
|-------------------|----------|---------------------------------------------------------|----------------------------------|
| `Content-Type`    | String   | Tipo do payload da requisição                           | `application/json`               |
| `X-API-Key`       | String   | Chave de autenticação do sistema cliente                | `sk_live_abc123xyz`              |
| `X-Correlation-Id`| String   | Identificador de correlação para rastreabilidade (opcional) | `550e8400-e29b-41d4-a716-446655440000` |

---

### 7.2 POST /sms/send — Envio de SMS

**Método:** `POST`
**Path:** `/sms/send`
**Descrição:** Recebe uma requisição de envio de SMS, valida os dados e enfileira para processamento assíncrono.

#### Request Body

```json
{
  "to": "+5511999999999",
  "message": "Seu código de verificação é: 123456",
  "sender": "CENTRAL",
  "metadata": {
    "campaignId": "camp-2026-q1",
    "customField": "valor-livre"
  }
}
```

| Campo       | Tipo             | Obrigatório | Validação                                      | Descrição                                        |
|-------------|------------------|-------------|------------------------------------------------|--------------------------------------------------|
| `to`        | String           | Sim         | Formato E.164, máx. 20 chars, regex `\+[1-9]\d{7,14}` | Número do destinatário                  |
| `message`   | String           | Sim         | 1–160 caracteres (ajustável por config)        | Conteúdo da mensagem SMS                         |
| `sender`    | String           | Não         | Máx. 11 chars alfanuméricos                    | Identificador do remetente (sender ID)           |
| `metadata`  | Object (livre)   | Não         | Mapa de chave-valor string                     | Dados adicionais para rastreamento pelo cliente  |

#### Response Body — Sucesso (HTTP 202 Accepted)

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED",
  "queuedAt": "2026-04-28T14:30:00Z"
}
```

#### Response Body — Erros

**HTTP 400 Bad Request** (validação)
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Request inválida",
  "details": [
    { "field": "to", "message": "Número de telefone em formato inválido" },
    { "field": "message", "message": "A mensagem não pode estar vazia" }
  ],
  "timestamp": "2026-04-28T14:30:00Z"
}
```

**HTTP 401 Unauthorized** (autenticação)
```json
{
  "error": "UNAUTHORIZED",
  "message": "API Key inválida ou ausente",
  "timestamp": "2026-04-28T14:30:00Z"
}
```

**HTTP 429 Too Many Requests** (rate limit)
```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Limite de requisições excedido",
  "retryAfter": 60,
  "timestamp": "2026-04-28T14:30:00Z"
}
```

**HTTP 503 Service Unavailable** (falha no broker)
```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Serviço temporariamente indisponível. Tente novamente em instantes.",
  "timestamp": "2026-04-28T14:30:00Z"
}
```

#### Códigos HTTP

| Código | Condição                                           |
|--------|----------------------------------------------------|
| 202    | Mensagem aceita e enfileirada com sucesso          |
| 400    | Dados de entrada inválidos                         |
| 401    | API Key ausente ou inválida                        |
| 429    | Taxa de requisições excedida                       |
| 503    | Serviço indisponível (ex: RabbitMQ inacessível)    |

---

### 7.3 GET /sms/{messageId}/status — Consulta de Status

**Método:** `GET`
**Path:** `/sms/{messageId}/status`
**Descrição:** Retorna o status atual de uma mensagem SMS previamente enfileirada.

#### Path Parameters

| Parâmetro   | Tipo   | Descrição                                  |
|-------------|--------|--------------------------------------------|
| `messageId` | String | UUID da mensagem retornado no envio (7.2)  |

#### Response Body — Sucesso (HTTP 200 OK)

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "to": "+5511999999999",
  "status": "DELIVERED",
  "queuedAt": "2026-04-28T14:30:00Z",
  "sentAt": "2026-04-28T14:30:05Z",
  "deliveredAt": "2026-04-28T14:30:12Z"
}
```

#### Códigos HTTP

| Código | Condição                              |
|--------|---------------------------------------|
| 200    | Mensagem encontrada                   |
| 401    | API Key ausente ou inválida           |
| 404    | messageId não encontrado              |

---

### 7.4 POST /callback/webhook — Recebimento de Webhook

**Método:** `POST`
**Path:** `/callback/webhook`
**Descrição:** Endpoint interno do `callback` que recebe notificações de entrega do provider via Webhook HTTP.

> **Nota:** Este endpoint não é exposto diretamente ao SDK cliente. O acesso deve ser restrito ao IP do provider via firewall/WAF.

#### Request Body (enviado pelo provider)

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "providerMessageId": "provider-internal-id-xyz",
  "phoneNumber": "+5511999999999",
  "status": "DELIVERED",
  "statusCode": "000",
  "deliveredAt": "2026-04-28T14:30:12Z"
}
```

#### Headers Esperados do Provider

| Header                   | Descrição                                              |
|--------------------------|--------------------------------------------------------|
| `X-Provider-Signature`   | Assinatura HMAC-SHA256 do payload para autenticação    |
| `Content-Type`           | `application/json`                                     |

#### Códigos HTTP

| Código | Condição                                              |
|--------|-------------------------------------------------------|
| 200    | Notificação recebida e processada com sucesso         |
| 400    | Payload inválido ou assinatura HMAC inválida          |
| 409    | Notificação duplicada (messageId já processado)       |
| 500    | Erro interno — provider deve reenviar após timeout    |

---

### 7.5 Exemplos cURL

<!-- EXEMPLOS cURL POR ENDPOINT -->

```bash
# [A PREENCHER] POST /sms/send — Envio de SMS
# Substituir <API_KEY> pela chave real e <BASE_URL> pelo endpoint do ambiente
# curl -X POST <BASE_URL>/sms/send \
#   -H "Content-Type: application/json" \
#   -H "X-API-Key: <API_KEY>" \
#   -H "X-Correlation-Id: $(uuidgen)" \
#   -d '{
#     "to": "+5511999999999",
#     "message": "Seu código é: 123456",
#     "sender": "CENTRAL"
#   }'
```

```bash
# [A PREENCHER] GET /sms/{messageId}/status — Consulta de Status
# Substituir <MESSAGE_ID> pelo UUID retornado no envio
# curl -X GET <BASE_URL>/sms/<MESSAGE_ID>/status \
#   -H "X-API-Key: <API_KEY>"
```

```bash
# [A PREENCHER] POST /callback/webhook — Simulação de Webhook (ambiente de dev/staging)
# Para simular chamada do provider em desenvolvimento local:
# curl -X POST <BASE_URL>/callback/webhook \
#   -H "Content-Type: application/json" \
#   -H "X-Provider-Signature: <HMAC_ASSINATURA>" \
#   -d '{
#     "messageId": "<MESSAGE_ID>",
#     "providerMessageId": "prov-123",
#     "phoneNumber": "+5511999999999",
#     "status": "DELIVERED",
#     "deliveredAt": "2026-04-28T14:30:12Z"
#   }'
```

---

## 8. Guia de Integração — Client Java SDK

### 8.1 Adicionando a Dependência

#### Maven

```xml
<!-- [A PREENCHER] Substituir <GROUP_ID>, <ARTIFACT_ID> e <VERSION> pelos valores do artefato publicado -->
<dependency>
    <groupId><!-- [A PREENCHER] ex: com.empresa.sms --></groupId>
    <artifactId><!-- [A PREENCHER] ex: sms-client-java --></artifactId>
    <version><!-- [A PREENCHER] ex: 1.0.0 --></version>
</dependency>
```

#### Gradle (Kotlin DSL)

```kotlin
// [A PREENCHER] Substituir pelos valores corretos do artefato
dependencies {
    implementation("/* [A PREENCHER] ex: com.empresa.sms */: /* sms-client-java */: /* 1.0.0 */")
}
```

> **Nota para a equipe:** Publicar o artefato no repositório Maven interno (Nexus/Artifactory) e documentar a URL do repositório, credenciais de acesso e política de versionamento semântico.

---

### 8.2 Configuração via application.properties (Spring Boot)

```properties
# [A PREENCHER] Configurações do SMS Client para aplicações Spring Boot
# sms.client.base-url=https://api.central-sms.empresa.com
# sms.client.api-key=${SMS_API_KEY}
# sms.client.timeout.connect=5000
# sms.client.timeout.read=10000
# sms.client.retry.max-attempts=3
# sms.client.retry.backoff-ms=1000
```

### 8.3 Configuração Programática (sem Spring)

```java
// [A PREENCHER] Exemplo de instanciação manual do SmsClient
// SmsClientConfig config = SmsClientConfig.builder()
//     .baseUrl("https://api.central-sms.empresa.com")
//     .apiKey(System.getenv("SMS_API_KEY"))
//     .connectTimeout(Duration.ofSeconds(5))
//     .readTimeout(Duration.ofSeconds(10))
//     .retryMaxAttempts(3)
//     .build();
// SmsClient smsClient = new SmsClient(config);
```

---

### 8.4 Métodos Disponíveis no SDK

| Método                                         | Retorno              | Descrição                                        |
|------------------------------------------------|----------------------|--------------------------------------------------|
| `send(SendSmsRequest request)`                 | `SendSmsResponse`    | Envia uma mensagem SMS                           |
| `getStatus(String messageId)`                  | `SmsStatusResponse`  | Consulta o status de uma mensagem por ID         |
| `sendAsync(SendSmsRequest request)`            | `CompletableFuture<SendSmsResponse>` | Versão assíncrona do envio          |

#### Classe SendSmsRequest

```java
// [A PREENCHER] Verificar campos e anotações na implementação final do SDK
// public class SendSmsRequest {
//     private String to;           // Obrigatório — número E.164
//     private String message;      // Obrigatório — conteúdo do SMS
//     private String sender;       // Opcional — sender ID
//     private Map<String, String> metadata; // Opcional — metadados livres
// }
```

#### Classe SendSmsResponse

```java
// [A PREENCHER] Verificar campos na implementação final do SDK
// public class SendSmsResponse {
//     private String messageId;   // UUID gerado pela Central
//     private String status;      // Ex: "QUEUED"
//     private Instant queuedAt;   // Timestamp de enfileiramento
// }
```

---

### 8.5 Exemplos de Uso

<!-- EXEMPLOS DE USO DO CLIENT JAVA SDK -->

```java
// [A PREENCHER] Exemplo de envio de SMS simples
// SmsClient client = ... // instância configurada (via Spring @Autowired ou manual)
// SendSmsRequest request = SendSmsRequest.builder()
//     .to("+5511999999999")
//     .message("Seu código de verificação é: 123456")
//     .sender("EMPRESA")
//     .build();
// SendSmsResponse response = client.send(request);
// System.out.println("Mensagem enfileirada: " + response.getMessageId());
```

```java
// [A PREENCHER] Exemplo de consulta de status de mensagem
// SmsStatusResponse status = client.getStatus("550e8400-e29b-41d4-a716-446655440000");
// System.out.println("Status atual: " + status.getStatus());
// if ("DELIVERED".equals(status.getStatus())) {
//     System.out.println("Entregue em: " + status.getDeliveredAt());
// }
```

```java
// [A PREENCHER] Exemplo de envio assíncrono com tratamento de erro
// client.sendAsync(request)
//     .thenAccept(resp -> log.info("Enfileirado: {}", resp.getMessageId()))
//     .exceptionally(ex -> {
//         if (ex.getCause() instanceof SmsValidationException) {
//             log.error("Dados inválidos: {}", ex.getMessage());
//         } else {
//             log.error("Falha no envio: {}", ex.getMessage());
//         }
//         return null;
//     });
```

```java
// [A PREENCHER] Exemplo com metadados de rastreamento de campanha
// SendSmsRequest request = SendSmsRequest.builder()
//     .to("+5511999999999")
//     .message("Promoção exclusiva! Acesse: https://empresa.com/promo")
//     .metadata(Map.of(
//         "campaignId", "BLACK-FRIDAY-2026",
//         "segmentId", "seg-premium"
//     ))
//     .build();
// SendSmsResponse response = client.send(request);
```

---

## 9. Configurações e Variáveis de Ambiente

### 9.1 api-rest

| Variável                          | Descrição                                              | Exemplo de Valor                              | Obrigatório |
|-----------------------------------|--------------------------------------------------------|-----------------------------------------------|-------------|
| `SERVER_PORT`                     | Porta HTTP do serviço                                  | `8080`                                        | Não (default 8080) |
| `RABBITMQ_HOST`                   | Host do servidor RabbitMQ                              | `rabbitmq.internal`                           | Sim         |
| `RABBITMQ_PORT`                   | Porta AMQP do RabbitMQ                                 | `5672`                                        | Não (default 5672) |
| `RABBITMQ_USERNAME`               | Usuário de autenticação no RabbitMQ                    | `sms_producer`                                | Sim         |
| `RABBITMQ_PASSWORD`               | Senha de autenticação no RabbitMQ                      | `[secret]`                                    | Sim         |
| `RABBITMQ_VIRTUAL_HOST`           | Virtual host do RabbitMQ                               | `/sms`                                        | Sim         |
| `SMS_EXCHANGE_NAME`               | Nome da Exchange de publicação                         | `sms.exchange`                                | Sim         |
| `SMS_QUEUE_NAME`                  | Nome da fila de destino                                | `sms.queue`                                   | Sim         |
| `SMS_API_KEY_HEADER`              | Nome do header de autenticação esperado                | `X-API-Key`                                   | Não (default) |
| `SMS_API_KEYS`                    | Lista de API Keys válidas (separadas por vírgula)      | `sk_live_abc,sk_live_xyz`                     | Sim         |
| `SMS_MAX_MESSAGE_LENGTH`          | Tamanho máximo da mensagem (caracteres)                | `160`                                         | Não (default 160) |
| `MANAGEMENT_ENDPOINTS_ENABLED`    | Endpoints do Actuator expostos                         | `health,info,metrics`                         | Não         |

### 9.2 worker

| Variável                          | Descrição                                              | Exemplo de Valor                              | Obrigatório |
|-----------------------------------|--------------------------------------------------------|-----------------------------------------------|-------------|
| `SERVER_PORT`                     | Porta HTTP (apenas Actuator)                           | `8081`                                        | Não         |
| `RABBITMQ_HOST`                   | Host do servidor RabbitMQ                              | `rabbitmq.internal`                           | Sim         |
| `RABBITMQ_PORT`                   | Porta AMQP                                             | `5672`                                        | Não         |
| `RABBITMQ_USERNAME`               | Usuário de autenticação no RabbitMQ                    | `sms_consumer`                                | Sim         |
| `RABBITMQ_PASSWORD`               | Senha de autenticação no RabbitMQ                      | `[secret]`                                    | Sim         |
| `RABBITMQ_VIRTUAL_HOST`           | Virtual host do RabbitMQ                               | `/sms`                                        | Sim         |
| `SMS_QUEUE_NAME`                  | Fila de consumo                                        | `sms.queue`                                   | Sim         |
| `SMS_DLQ_NAME`                    | Fila de dead letter                                    | `sms.queue.dlq`                               | Sim         |
| `RABBITMQ_PREFETCH_COUNT`         | Número de mensagens pré-carregadas por worker          | `10`                                          | Não         |
| `PROVIDER_BASE_URL`               | URL base da API do provider externo                    | `https://api.provider.com`                    | Sim         |
| `PROVIDER_API_KEY`                | Chave de autenticação no provider                      | `[secret]`                                    | Sim         |
| `PROVIDER_TIMEOUT_CONNECT_MS`     | Timeout de conexão com o provider (ms)                 | `3000`                                        | Não         |
| `PROVIDER_TIMEOUT_READ_MS`        | Timeout de leitura da resposta do provider (ms)        | `10000`                                       | Não         |
| `CIRCUIT_BREAKER_FAILURE_THRESHOLD` | % de falhas para abertura do circuito               | `50`                                          | Não         |
| `CIRCUIT_BREAKER_WAIT_DURATION_MS`  | Tempo em estado OPEN antes de HALF-OPEN (ms)        | `30000`                                       | Não         |
| `RETRY_MAX_ATTEMPTS`              | Número máximo de tentativas no provider                | `4`                                           | Não         |
| `AWS_REGION`                      | Região AWS onde o SNS está provisionado                | `us-east-1`                                   | Sim         |
| `AWS_SNS_TOPIC_ARN`               | ARN do tópico SNS de entrega                           | `arn:aws:sns:us-east-1:123456:sms-delivery`   | Sim         |
| `AWS_ACCESS_KEY_ID`               | Access Key AWS (prefira IAM Role em produção)          | `[secret]`                                    | Condicional |
| `AWS_SECRET_ACCESS_KEY`           | Secret Access Key AWS                                  | `[secret]`                                    | Condicional |

### 9.3 callback

| Variável                          | Descrição                                              | Exemplo de Valor                              | Obrigatório |
|-----------------------------------|--------------------------------------------------------|-----------------------------------------------|-------------|
| `SERVER_PORT`                     | Porta HTTP do serviço (webhook + Actuator)             | `8082`                                        | Não         |
| `DB_URL`                          | JDBC URL do banco de dados PostgreSQL                  | `jdbc:postgresql://db.internal:5432/sms`      | Sim         |
| `DB_USERNAME`                     | Usuário do banco de dados                              | `sms_callback`                                | Sim         |
| `DB_PASSWORD`                     | Senha do banco de dados                                | `[secret]`                                    | Sim         |
| `DB_POOL_SIZE`                    | Tamanho máximo do pool de conexões (HikariCP)          | `20`                                          | Não         |
| `AWS_REGION`                      | Região AWS onde o SQS está provisionado                | `us-east-1`                                   | Sim         |
| `AWS_SQS_QUEUE_URL`               | URL da fila SQS de notificações de entrega             | `https://sqs.us-east-1.amazonaws.com/123/sms-delivery-queue` | Sim |
| `AWS_SQS_DLQ_URL`                 | URL da DLQ do SQS                                      | `https://sqs.us-east-1.amazonaws.com/123/sms-delivery-queue-dlq` | Não |
| `AWS_ACCESS_KEY_ID`               | Access Key AWS (prefira IAM Role em produção)          | `[secret]`                                    | Condicional |
| `AWS_SECRET_ACCESS_KEY`           | Secret Access Key AWS                                  | `[secret]`                                    | Condicional |
| `WEBHOOK_SECRET_KEY`              | Chave secreta para validação de assinatura HMAC        | `[secret]`                                    | Sim         |
| `FLYWAY_ENABLED`                  | Habilita migrações automáticas do Flyway               | `true`                                        | Não         |

---

## 10. Considerações de Segurança

### 10.1 Autenticação e Autorização na api-rest

**API Key:**
A `api-rest` adota autenticação via API Key estática transmitida no header `X-API-Key`. As chaves são armazenadas em variável de ambiente (jamais em código-fonte ou repositório). Em produção, as chaves devem ser gerenciadas via secret manager (AWS Secrets Manager, HashiCorp Vault).

**Boas práticas:**
- Cada sistema cliente deve possuir sua própria API Key — jamais compartilhar chaves entre sistemas;
- Implementar rate limiting por API Key para prevenir abuso;
- Registrar em log (sem expor o valor completo) o identificador da chave usada em cada requisição;
- Rotacionar chaves periodicamente e revogar imediatamente em caso de comprometimento.

**OAuth2 / JWT (roadmap):**
Para cenários multi-tenant com controle granular de permissões, recomenda-se evoluir para autenticação OAuth2 com JWT, utilizando Spring Security OAuth2 Resource Server.

### 10.2 Segurança no Webhook do Callback

- **Validação de assinatura HMAC-SHA256:** O provider assina o payload com uma chave secreta compartilhada. O `callback` deve recomputar a assinatura e rejeitar (HTTP 400) qualquer chamada com assinatura inválida;
- **Allowlist de IPs:** Restringir o acesso ao endpoint `/callback/webhook` apenas aos IPs do provider via regra de firewall ou WAF;
- **HTTPS obrigatório:** Todo tráfego HTTP deve ser forçado a HTTPS via redirecionamento ou configuração de load balancer.

### 10.3 Segurança em Filas e Tópicos AWS (IAM Policies)

Adotar o princípio do **menor privilégio** para as políticas IAM:

**worker — permissões mínimas:**
```json
{
  "Effect": "Allow",
  "Action": ["sns:Publish"],
  "Resource": "arn:aws:sns:REGION:ACCOUNT:sms-delivery-topic"
}
```

**callback — permissões mínimas:**
```json
{
  "Effect": "Allow",
  "Action": [
    "sqs:ReceiveMessage",
    "sqs:DeleteMessage",
    "sqs:GetQueueAttributes"
  ],
  "Resource": "arn:aws:sqs:REGION:ACCOUNT:sms-delivery-queue"
}
```

- Utilizar **IAM Roles** para instâncias EC2 ou tarefas ECS em vez de Access Keys estáticas;
- Nunca embutir Access Keys em imagens Docker ou arquivos de configuração versionados;
- Habilitar **CloudTrail** para auditoria de chamadas à API AWS.

### 10.4 Proteção de Dados Sensíveis em Logs

- **Nunca logar o conteúdo completo da mensagem SMS** — logar apenas o `messageId` e os primeiros 4 dígitos do número de telefone (ex: `+5511****5678`);
- **Mascarar API Keys nos logs:** registrar apenas os últimos 4 caracteres;
- Utilizar MDC (Mapped Diagnostic Context) do SLF4J para propagar o `correlationId` automaticamente em todos os logs da requisição;
- Configurar nível de log `INFO` em produção; `DEBUG` apenas em ambientes de desenvolvimento;
- Armazenar logs em sistema centralizado (ex: AWS CloudWatch Logs, ELK Stack) com controle de acesso por perfil.

---

## 11. Considerações de Escalabilidade e Resiliência

### 11.1 Escalabilidade Horizontal do Worker

O `worker` é o componente de maior intensidade computacional e o gargalo natural do sistema em alto volume. Sua arquitetura stateless permite escalabilidade horizontal sem dependências de estado compartilhado.

**Estratégia:**
- Múltiplas réplicas do `worker` consumindo a mesma fila `sms.queue` no RabbitMQ (competing consumers);
- Configuração de `prefetch_count` por instância para controle de throughput;
- Auto-scaling baseado em profundidade da fila (métrica: `sms.queue.messages_ready`);
- Em Kubernetes: HPA (Horizontal Pod Autoscaler) baseado em métricas customizadas via KEDA + RabbitMQ plugin.

**Considerações:**
- O `correlationId` garante que o processamento de uma mensagem por múltiplos workers concorrentes não gere duplicidade de envio ao provider;
- O `prefetch_count` deve ser calibrado conforme o throughput do provider e a latência das chamadas HTTP.

### 11.2 Configuração de DLQ

#### RabbitMQ — Dead Letter Exchange

| Parâmetro                     | Valor sugerido         | Descrição                                              |
|-------------------------------|------------------------|--------------------------------------------------------|
| `x-dead-letter-exchange`      | `sms.dlx`              | Exchange de destino para mensagens com falha           |
| `x-dead-letter-routing-key`   | `sms.dlq`              | Routing key da fila de DLQ                             |
| `x-message-ttl`               | `86400000` (24h)       | TTL das mensagens na fila principal antes de expirar   |
| `x-max-delivery-count`        | `5`                    | Número máximo de entregas antes de mover para DLQ      |

#### AWS SQS — Dead Letter Queue

| Parâmetro                 | Valor sugerido | Descrição                                                  |
|---------------------------|----------------|------------------------------------------------------------|
| `maxReceiveCount`         | `5`            | Número de falhas antes de mover para DLQ                   |
| `MessageRetentionPeriod`  | `1209600` (14 dias) | Período de retenção na DLQ para análise e reprocessamento |

**Monitoramento das DLQs:**
- Alertas no CloudWatch para mensagens na `sms-delivery-queue-dlq` (threshold > 0);
- Alerta no RabbitMQ Management para `sms.queue.dlq` com mensagens acumuladas;
- Processo definido para análise, correção e reprocessamento manual ou automatizado de mensagens na DLQ.

### 11.3 Idempotência no Callback

O `callback` pode receber a mesma notificação múltiplas vezes (reentregas do SQS, retentativas do provider). A idempotência é garantida por:

1. **SELECT antes do INSERT:** Verificar existência do `message_id` no banco antes de qualquer escrita;
2. **UNIQUE constraint no banco:** `CONSTRAINT uq_message_id UNIQUE (message_id)` como última linha de defesa;
3. **UPSERT seguro (alternativa):** `INSERT ... ON CONFLICT (message_id) DO NOTHING` para operação atômica e sem condição de corrida;
4. **Resposta 200 para duplicatas:** O endpoint de webhook retorna HTTP 200 mesmo para notificações duplicadas, evitando que o provider reenvie indefinidamente.

### 11.4 Monitoramento Recomendado

#### Métricas

| Métrica                                  | Fonte          | Alerta sugerido                                     |
|------------------------------------------|----------------|-----------------------------------------------------|
| `sms.queue.messages_ready`               | RabbitMQ       | > 1000 mensagens acumuladas por 5 min               |
| `sms.queue.dlq.messages`                 | RabbitMQ       | > 0 mensagens (qualquer mensagem na DLQ)            |
| `worker.provider.calls.failure_rate`     | Micrometer     | > 10% de taxa de falhas nas chamadas ao provider    |
| `worker.circuit_breaker.state`           | Micrometer     | Estado OPEN por mais de 1 minuto                    |
| `callback.sms_delivery_queue.dlq.depth` | CloudWatch SQS | > 0 mensagens                                       |
| `api_rest.http.requests.5xx`             | Micrometer     | > 1% das requisições com erro 5xx                   |
| `callback.db.pool.pending_connections`   | HikariCP       | > 80% de utilização do pool                         |

#### Health Checks (Spring Actuator)

Todos os microsserviços devem expor `GET /actuator/health` com verificações de:
- Conectividade com RabbitMQ (api-rest, worker)
- Conectividade com AWS SQS/SNS (worker, callback)
- Conectividade com banco de dados (callback)
- Disponibilidade do provider (worker — health check leve)

#### Rastreamento Distribuído

Integrar **OpenTelemetry** com propagação automática do `correlationId` / `traceId` via headers W3C Trace Context para rastreamento fim-a-fim entre todos os microsserviços (compatible com Jaeger, Zipkin, AWS X-Ray).

---

## 12. Glossário

| Termo                          | Definição                                                                                                     |
|--------------------------------|---------------------------------------------------------------------------------------------------------------|
| **ACK (Acknowledge)**          | Confirmação de processamento de uma mensagem, sinalizando ao broker que ela pode ser removida da fila         |
| **AMQP**                       | Advanced Message Queuing Protocol — protocolo de comunicação utilizado pelo RabbitMQ                          |
| **API Key**                    | Token de autenticação estático utilizado para identificar e autorizar sistemas clientes                        |
| **ARN**                        | Amazon Resource Name — identificador único de recursos na AWS                                                 |
| **Circuit Breaker**            | Padrão de resiliência que interrompe chamadas a um serviço defeituoso por um período, evitando sobrecarga      |
| **Competing Consumers**        | Padrão onde múltiplos consumidores concorrem para processar mensagens de uma mesma fila                        |
| **Correlation ID**             | Identificador único propagado em todos os saltos de um fluxo para rastreabilidade fim-a-fim                   |
| **Dead Letter Queue (DLQ)**    | Fila especial que armazena mensagens que falharam no processamento após N tentativas, para análise posterior    |
| **E.164**                      | Padrão internacional de formatação de números de telefone (ex: +5511999999999)                                |
| **Exchange (RabbitMQ)**        | Ponto de entrada de mensagens no RabbitMQ; roteia mensagens para filas conforme regras (bindings)             |
| **Fanout**                     | Estratégia de entrega onde uma mensagem publicada é replicada para todos os assinantes                         |
| **Flyway**                     | Ferramenta de versionamento e migração de schema de banco de dados relacional                                  |
| **HPA**                        | Horizontal Pod Autoscaler — recurso Kubernetes para escalonamento automático baseado em métricas               |
| **IAM**                        | AWS Identity and Access Management — serviço de controle de acesso a recursos AWS                             |
| **Idempotência**               | Propriedade de uma operação cujo resultado é o mesmo independentemente do número de execuções                  |
| **JDBC**                       | Java Database Connectivity — API Java padrão para acesso a bancos de dados relacionais                         |
| **KEDA**                       | Kubernetes Event-Driven Autoscaling — extensão Kubernetes para auto-scaling baseado em métricas de eventos     |
| **Microsserviço**              | Unidade de software autônoma, com responsabilidade única, implantada e escalada independentemente               |
| **NACK (Negative Acknowledge)** | Sinalização ao broker de que uma mensagem não pôde ser processada, devendo ser reencaminhada ou descartada    |
| **OpenTelemetry**              | Framework de observabilidade para coleta de traces, métricas e logs em sistemas distribuídos                   |
| **Prefetch Count**             | Número de mensagens que o RabbitMQ entrega a um consumidor antes de aguardar ACK                               |
| **Producer/Consumer**          | Padrão arquitetural onde um componente publica mensagens (producer) e outro as processa (consumer)             |
| **RabbitMQ**                   | Broker de mensagens open-source baseado em AMQP, utilizado para comunicação assíncrona interna                 |
| **Rate Limiting**              | Controle de taxa de requisições para prevenir abuso e garantir disponibilidade do serviço                      |
| **SDK (Software Dev. Kit)**    | Conjunto de ferramentas, bibliotecas e documentação para integração com uma plataforma ou serviço              |
| **SLA**                        | Service Level Agreement — acordo de nível de serviço que define metas de disponibilidade e desempenho          |
| **SMS**                        | Short Message Service — serviço de mensagens curtas para telefonia móvel                                       |
| **SNS**                        | AWS Simple Notification Service — serviço gerenciado de mensageria publish/subscribe da AWS                    |
| **Spring AMQP**                | Módulo Spring para integração com brokers AMQP (RabbitMQ)                                                     |
| **Spring Cloud AWS**           | Módulo Spring para integração com serviços AWS (SQS, SNS, S3, etc.)                                           |
| **SQS**                        | AWS Simple Queue Service — serviço gerenciado de filas de mensagens da AWS                                     |
| **Surrogate Key**              | Chave primária artificial (ex: auto-increment) sem significado de negócio                                      |
| **Visibility Timeout**         | Período durante o qual uma mensagem SQS consumida fica invisível para outros consumidores                      |
| **WAF**                        | Web Application Firewall — firewall para proteção de aplicações web contra ataques comuns                      |
| **Webhook**                    | Mecanismo de notificação HTTP onde um sistema externo chama um endpoint do receptor em eventos específicos      |
| **Worker**                     | Processo em background responsável por consumir e processar tarefas de uma fila de forma assíncrona            |

---

*Documento gerado em 2026-04-28 — versão 1.0.0*
*Para alterações ou contribuições, contatar a equipe de Arquitetura de Software.*
