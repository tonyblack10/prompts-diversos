# Guia Prático de Query de Logs no Datadog

## Fundamentos da Sintaxe de Busca

A pesquisa de logs no Datadog utiliza uma sintaxe baseada em Apache Lucene. A estrutura básica é simples e poderosa.

### Busca de Texto Livre

Para buscar qualquer termo nos logs, basta digitá-lo diretamente:

```
error
```

Isso retorna todos os logs que contêm a palavra "error" em qualquer campo.

### Busca em Campos Específicos

Use a sintaxe `campo:valor` para buscar em atributos específicos:

```
status:error
host:web-server-01
service:payment-api
```

## Operadores Lógicos

### AND (E)

Combine múltiplas condições que devem ser todas verdadeiras:

```
status:error AND service:checkout
error AND host:prod-*
```

### OR (OU)

Busque logs que atendam qualquer uma das condições:

```
status:error OR status:warning
service:api-user OR service:api-payment
```

### NOT (NÃO)

Exclua resultados específicos:

```
status:error NOT service:monitoring
error NOT host:staging-*
```

### Parênteses para Agrupamento

Combine operadores com lógica complexa:

```
(status:error OR status:critical) AND service:database
service:api AND (method:POST OR method:PUT) NOT path:/health
```

## Wildcards e Padrões

### Asterisco (*)

Representa zero ou mais caracteres:

```
host:web-*
user:john*
path:/api/*/users
```

### Interrogação (?)

Representa exatamente um caractere:

```
error_code:50?
host:server-0?
```

## Busca por Ranges

### Numéricos

Use colchetes para ranges inclusivos e chaves para exclusivos:

```
http.status_code:[400 TO 499]
response_time:[1000 TO *]
duration:{0 TO 100}
```

### Datas e Timestamps

```
@timestamp:[now-1h TO now]
@timestamp:[2024-01-01 TO 2024-01-31]
```

## Facetas e Atributos

### Atributos Padrão

O Datadog indexa automaticamente alguns campos:

```
@http.status_code:500
@network.client.ip:192.168.1.100
@error.kind:DatabaseException
```

### Tags

Busque por tags específicas:

```
env:production
version:2.1.0
team:backend
```

### Atributos Customizados

Se você configurou atributos personalizados:

```
@user.id:12345
@transaction.amount:>1000
@order.status:pending
```

## Busca em JSON

Para logs estruturados em JSON, acesse campos aninhados com ponto:

```
@metadata.user.email:*@example.com
@request.headers.user-agent:*Chrome*
@response.data.success:true
```

## Queries Avançadas

### Expressões Regulares

Use a sintaxe com barras para regex:

```
host:/web-server-[0-9]{2}/
email:/@gmail\.com$/
```

### Existência de Campos

Verifique se um campo existe:

```
_exists_:@user.id
_missing_:@error.stack_trace
```

### Escape de Caracteres Especiais

Para buscar caracteres especiais, use barra invertida:

```
message:"error\: connection failed"
path:"/api/v1/users\[id\]"
```

## Filtros de Tempo

### Usando a Interface

A barra de tempo no topo permite selecionar:
- Últimos 15 minutos
- Última hora
- Últimas 24 horas
- Últimos 7 dias
- Range customizado

### Na Query

```
@timestamp:>now-1h
@timestamp:<now-30m
```

## Exemplos Práticos por Caso de Uso

### Debugging de Erros em Produção

```
status:error AND env:production AND service:checkout NOT path:/health
```

### Análise de Performance

```
@duration:>5000 AND service:api env:production
```

### Segurança - Tentativas de Login Falhas

```
@event.action:login AND @event.outcome:failure @network.client.ip:*
```

### Monitoramento de APIs

```
@http.method:POST @http.status_code:[500 TO 599] service:api-*
```

### Tracking de Usuários Específicos

```
@user.id:12345 AND (status:error OR @duration:>3000)
```

### Erros de Database

```
@error.kind:*DatabaseException* OR @error.kind:*SQLException* env:production
```

### Requests Lentas

```
@duration:>10000 @http.method:(GET OR POST) service:web-app
```

## Salvando e Reutilizando Queries

### Saved Views

1. Configure sua query e filtros
2. Clique em "Save View"
3. Nomeie e compartilhe com o time

### Queries Favoritas

Use o botão de estrela para marcar queries frequentes.

## Agregações e Análises

### Group By

Agrupe logs por atributos específicos usando a interface:

- Clique em qualquer faceta
- Selecione "Group by"
- Escolha a métrica (count, percentile, etc.)

### Patterns

O Datadog detecta automaticamente padrões em logs:

```
service:api status:error
```

Depois vá para a aba "Patterns" para ver agrupamentos automáticos.

## Dicas de Performance

### Seja Específico

Quanto mais específica a query, mais rápido:

```
✅ service:checkout status:error env:production @timestamp:>now-1h
❌ error
```

### Use Índices

Configure índices para campos frequentemente buscados no Log Configuration.

### Limite o Período

Queries em períodos menores são mais rápidas:
- Use `now-1h` em vez de `now-7d` quando possível

### Aproveite Tags

Tags são indexadas e buscadas rapidamente:

```
✅ env:production service:api
❌ @message:*production* AND @message:*api*
```

## Troubleshooting Comum

### Query Não Retorna Resultados

1. Verifique o período de tempo selecionado
2. Confirme que o serviço está enviando logs
3. Valide a sintaxe dos campos (case-sensitive)
4. Remova filtros um por um para identificar o problema

### Performance Lenta

1. Reduza o período de tempo
2. Adicione filtros mais específicos (service, env)
3. Use facetas indexadas
4. Evite wildcards no início de termos (`*error` é lento)

### Campos Não Aparecem

1. Verifique se o campo está sendo enviado nos logs
2. Confirme a configuração de parsing no Log Pipeline
3. Aguarde alguns minutos para indexação

## Integração com Alertas

Você pode usar queries para criar monitores:

1. Configure sua query no Log Explorer
2. Clique em "Export to Monitor"
3. Defina thresholds e notificações

Exemplo de query para alerta:

```
status:error service:payment-api env:production
```

Configure para alertar quando count > 50 em 5 minutos.

## Conclusão

O domínio da sintaxe de query do Datadog é essencial para troubleshooting eficiente e observabilidade proativa. Pratique combinando diferentes operadores e aproveite as facetas para análises mais profundas.

**Atalhos Úteis:**
- `Ctrl/Cmd + K`: Abrir busca rápida
- Clique em qualquer valor nos logs: Adiciona à query
- `Shift + Click`: Exclui da query (NOT)