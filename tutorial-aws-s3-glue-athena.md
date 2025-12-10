# Tutorial Completo: Workflow S3 + Glue + Athena com Particionamento

Vou criar um guia detalhado para implementar um pipeline de dados completo na AWS usando apenas a interface administrativa.

## Visão Geral do Workflow

Nosso pipeline seguirá este fluxo:
1. **S3**: Armazenamento de arquivos JSON
2. **Glue Crawler**: Catalogação automática dos dados
3. **Glue ETL Job**: Transformação de JSON para Parquet com particionamento
4. **Athena**: Consultas SQL nos dados otimizados

---

## Parte 1: Configuração do Amazon S3

### 1.1 Criar Buckets S3

1. Acesse o **Console AWS** → **S3**
2. Clique em **"Criar bucket"**

**Bucket de Entrada (dados brutos em JSON):**
- Nome: `meu-dados-json-raw`
- Região: escolha sua região preferida (ex: us-east-1)
- Deixe as demais configurações padrão
- Clique em **"Criar bucket"**

**Bucket de Saída (dados transformados em Parquet):**
- Nome: `meu-dados-parquet-processado`
- Mesma região do bucket anterior
- Clique em **"Criar bucket"**

### 1.2 Criar Estrutura de Pastas com Particionamento

No bucket `meu-dados-json-raw`:

1. Clique no nome do bucket
2. Clique em **"Criar pasta"**
3. Crie a seguinte estrutura:
   - `vendas/ano=2024/mes=01/`
   - `vendas/ano=2024/mes=02/`
   - `vendas/ano=2024/mes=03/`

### 1.3 Upload de Arquivos JSON de Exemplo

1. Entre na pasta `vendas/ano=2024/mes=01/`
2. Clique em **"Carregar"**
3. Clique em **"Adicionar arquivos"**
4. Selecione um arquivo JSON (exemplo de estrutura abaixo)
5. Clique em **"Carregar"**

**Exemplo de estrutura JSON** (crie um arquivo `vendas_01.json`):
```json
{"id": 1, "produto": "Notebook", "valor": 3500.00, "cliente": "João Silva", "data": "2024-01-15"}
{"id": 2, "produto": "Mouse", "valor": 50.00, "cliente": "Maria Santos", "data": "2024-01-16"}
{"id": 3, "produto": "Teclado", "valor": 150.00, "cliente": "Pedro Costa", "data": "2024-01-17"}
```

Repita o processo para as pastas de fevereiro e março com dados diferentes.

---

## Parte 2: Configuração do AWS Glue

### 2.1 Criar Database no Glue Data Catalog

1. Acesse **AWS Glue** no console
2. No menu lateral, clique em **"Databases"** (em Data Catalog)
3. Clique em **"Add database"**
4. Configure:
   - Nome: `db_vendas`
   - Descrição: `Database para dados de vendas`
5. Clique em **"Create database"**

### 2.2 Criar IAM Role para o Glue

1. Acesse **IAM** no console AWS
2. Clique em **"Roles"** no menu lateral
3. Clique em **"Create role"**
4. Configure:
   - Tipo de entidade confiável: **AWS service**
   - Caso de uso: **Glue**
5. Clique em **"Next"**
6. Adicione as seguintes políticas:
   - `AWSGlueServiceRole` (já selecionada)
   - `AmazonS3FullAccess`
7. Clique em **"Next"**
8. Nome da role: `GlueServiceRole-Vendas`
9. Clique em **"Create role"**

### 2.3 Criar Crawler para Catalogar Dados JSON

1. No AWS Glue, clique em **"Crawlers"** no menu lateral
2. Clique em **"Create crawler"**

**Passo 1 - Set crawler properties:**
- Nome: `crawler-vendas-json`
- Clique em **"Next"**

**Passo 2 - Choose data sources:**
- Clique em **"Add a data source"**
- Data source: **S3**
- Network connection: deixe em branco
- S3 path: `s3://meu-dados-json-raw/vendas/`
- Clique em **"Add an S3 data source"**
- Clique em **"Next"**

**Passo 3 - Configure security settings:**
- IAM role: selecione `GlueServiceRole-Vendas`
- Clique em **"Next"**

**Passo 4 - Set output and scheduling:**
- Target database: selecione `db_vendas`
- Table name prefix: `raw_` (opcional)
- Crawler schedule: **On demand** (por enquanto)
- Clique em **"Next"**

**Passo 5 - Review:**
- Revise as configurações
- Clique em **"Create crawler"**

### 2.4 Executar o Crawler

1. Na lista de Crawlers, selecione `crawler-vendas-json`
2. Clique em **"Run"**
3. Aguarde a conclusão (status mudará para "Ready")
4. Vá em **"Tables"** no menu lateral
5. Você verá uma nova tabela criada (ex: `raw_vendas` ou similar)

### 2.5 Verificar a Tabela Catalogada

1. Clique no nome da tabela criada
2. Verifique:
   - Schema (colunas detectadas)
   - Partições (ano e mes devem aparecer)
   - Localização S3

---

## Parte 3: Criar Job ETL no Glue para Converter JSON para Parquet

### 3.1 Criar ETL Job Visual

1. No AWS Glue, clique em **"ETL jobs"** no menu lateral
2. Clique em **"Visual ETL"**
3. Clique em **"Create job"**

### 3.2 Configurar o Job Visual (Diagrama de Fluxo)

**Node 1 - Data Source (Origem):**
1. No canvas, clique no botão **"+"** ou **"Sources"**
2. Selecione **"AWS Glue Data Catalog"**
3. Configure:
   - Nome do node: `Source - Vendas JSON`
   - Database: `db_vendas`
   - Table: selecione a tabela criada pelo crawler
4. Clique em **"Save"**

**Node 2 - Transform (opcional - para ajustes):**
1. Clique no node `Source - Vendas JSON`
2. Clique no **"+"** que aparece
3. Selecione **"Transform"** → **"Change Schema"**
4. Configure:
   - Nome: `Ajustar Schema`
   - Aqui você pode renomear colunas, mudar tipos, etc.
   - Por enquanto, deixe como está
5. Clique em **"Save"**

**Node 3 - Data Target (Destino):**
1. Clique no último node
2. Clique no **"+"**
3. Selecione **"Targets"** → **"Amazon S3"**
4. Configure:
   - Nome: `Target - Parquet S3`
   - Format: **Parquet**
   - Compression Type: **Snappy**
   - S3 Target Location: `s3://meu-dados-parquet-processado/vendas/`
   - Data Catalog update options:
     - Marque **"Create a table in the Data Catalog and on subsequent runs, update the schema and add new partitions"**
     - Database: `db_vendas`
     - Table name: `vendas_parquet`
   - Partition keys: **adicione as colunas de partição:**
     - `ano`
     - `mes`

### 3.3 Configurar Propriedades do Job

1. Clique na aba **"Job details"** (canto superior direito)
2. Configure:
   - Nome: `job-json-para-parquet-vendas`
   - IAM Role: `GlueServiceRole-Vendas`
   - Type: **Spark**
   - Glue version: **Glue 4.0** (ou a mais recente)
   - Language: **Python 3**
   - Worker type: **G.1X**
   - Number of workers: **2**
   - Job timeout: **30 minutos**
   - Number of retries: **0**

3. Clique em **"Save"** (canto superior direito)

### 3.4 Executar o Job

1. Clique em **"Run"** (canto superior direito)
2. Aguarde a execução (pode levar alguns minutos)
3. Acompanhe o status na aba **"Runs"**
4. Quando o status for **"Succeeded"**, os dados foram convertidos

### 3.5 Verificar Dados Convertidos no S3

1. Volte ao **S3**
2. Acesse o bucket `meu-dados-parquet-processado`
3. Entre na pasta `vendas/`
4. Você verá a estrutura particionada:
   - `ano=2024/mes=01/` com arquivos `.parquet`
   - `ano=2024/mes=02/` com arquivos `.parquet`
   - `ano=2024/mes=03/` com arquivos `.parquet`

### 3.6 Verificar Tabela no Data Catalog

1. Volte ao **AWS Glue**
2. Clique em **"Tables"**
3. Você verá a tabela `vendas_parquet`
4. Clique nela e verifique:
   - Schema
   - Partições criadas
   - Formato: Parquet

---

## Parte 4: Consultar Dados no Amazon Athena

### 4.1 Configurar Local de Resultados do Athena

1. Acesse o **Amazon Athena** no console
2. Se for a primeira vez, será solicitado configurar o local de resultados
3. Clique em **"Settings"** ou **"Manage"**
4. Configure:
   - Query result location: `s3://meu-dados-parquet-processado/athena-results/`
   - (Crie a pasta `athena-results` no bucket se necessário)
5. Clique em **"Save"**

### 4.2 Selecionar Database

1. Na interface do Athena, no painel esquerdo:
   - Database: selecione `db_vendas`
2. Você verá as tabelas:
   - Tabela JSON original
   - Tabela Parquet (`vendas_parquet`)

### 4.3 Executar Consultas SQL

**Consulta 1 - Ver todos os dados:**
```sql
SELECT * FROM vendas_parquet LIMIT 10;
```

**Consulta 2 - Consultar partição específica:**
```sql
SELECT * 
FROM vendas_parquet 
WHERE ano = 2024 AND mes = 1;
```

**Consulta 3 - Agregação por mês:**
```sql
SELECT 
    ano,
    mes,
    COUNT(*) as total_vendas,
    SUM(valor) as valor_total,
    AVG(valor) as valor_medio
FROM vendas_parquet
WHERE ano = 2024
GROUP BY ano, mes
ORDER BY mes;
```

**Consulta 4 - Top produtos por valor:**
```sql
SELECT 
    produto,
    COUNT(*) as quantidade,
    SUM(valor) as valor_total
FROM vendas_parquet
WHERE ano = 2024 AND mes = 1
GROUP BY produto
ORDER BY valor_total DESC;
```

### 4.4 Benefícios do Particionamento

Execute estas consultas e observe a diferença:

**Sem usar partição (escaneia todos os dados):**
```sql
SELECT * FROM vendas_parquet;
```

**Usando partição (escaneia apenas dados necessários):**
```sql
SELECT * FROM vendas_parquet WHERE ano = 2024 AND mes = 1;
```

No rodapé do Athena, você verá:
- **Data scanned**: quantidade de dados escaneados
- A consulta com partição escaneia MUITO menos dados = menor custo

---

## Parte 5: Automação e Manutenção

### 5.1 Agendar Crawler para Atualizar Partições

1. Volte ao **AWS Glue** → **Crawlers**
2. Selecione seu crawler
3. Clique em **"Actions"** → **"Edit crawler"**
4. Na seção de Schedule:
   - Frequency: **Daily** (ou conforme necessário)
   - Start time: escolha um horário
5. Salve as alterações

### 5.2 Criar Trigger para o Job ETL

1. No **AWS Glue**, vá em **"Triggers"**
2. Clique em **"Add trigger"**
3. Configure:
   - Nome: `trigger-diario-etl-vendas`
   - Trigger type: **Schedule**
   - Frequency: **Daily**
   - Start time: (30 minutos após o crawler)
4. Na próxima tela, selecione o job `job-json-para-parquet-vendas`
5. Clique em **"Add"**

### 5.3 Atualizar Partições Manualmente no Athena (se necessário)

Se adicionar novos dados e as partições não aparecerem:

```sql
MSCK REPAIR TABLE vendas_parquet;
```

Ou adicionar partição específica:

```sql
ALTER TABLE vendas_parquet 
ADD PARTITION (ano=2024, mes=4) 
LOCATION 's3://meu-dados-parquet-processado/vendas/ano=2024/mes=04/';
```

---

## Parte 6: Boas Práticas e Otimizações

### 6.1 Estrutura de Particionamento Ideal

✅ **Bom:**
- `ano=2024/mes=01/dia=15/`
- `regiao=sul/ano=2024/mes=01/`

❌ **Evite:**
- Partições com poucos dados (< 128MB)
- Partições demais (milhares)
- Colunas com alta cardinalidade (ex: ID do cliente)

### 6.2 Monitorar Custos

1. Acesse **AWS Cost Explorer**
2. Monitore:
   - Armazenamento S3
   - Execuções do Glue (cobrado por DPU-hora)
   - Queries do Athena (cobrado por TB escaneado)

### 6.3 Compressão de Dados

No job Glue, você já configurou:
- Formato: **Parquet** (compressão colunar)
- Compression: **Snappy** (bom equilíbrio)

Outras opções:
- **Gzip**: maior compressão, mais lento
- **LZO**: mais rápido, menos compressão

### 6.4 Versionamento de Dados

1. No bucket S3, ative versionamento:
   - Vá ao bucket → **Properties**
   - **Bucket Versioning** → **Enable**

---

## Parte 7: Troubleshooting Comum

### Problema 1: Crawler não encontra partições

**Solução:**
- Verifique se a estrutura de pastas está correta: `chave=valor/`
- Execute: `MSCK REPAIR TABLE` no Athena

### Problema 2: Job Glue falha

**Solução:**
- Verifique logs no **CloudWatch Logs**
- Confirme permissões da IAM Role
- Verifique se o formato JSON está correto

### Problema 3: Athena retorna "HIVE_PARTITION_SCHEMA_MISMATCH"

**Solução:**
- Schema das partições não coincide
- Delete e recrie a tabela ou use `MSCK REPAIR TABLE`

### Problema 4: Custo alto no Athena

**Solução:**
- Use partições nas queries: `WHERE ano=2024 AND mes=1`
- Use formato Parquet (já implementado)
- Limite resultados com `LIMIT`
- Use colunas específicas em vez de `SELECT *`

---

## Resumo do Workflow Completo

```
1. Dados JSON → S3 (meu-dados-json-raw/vendas/ano=2024/mes=01/)
                ↓
2. Glue Crawler → Cataloga dados e detecta partições
                ↓
3. Glue Data Catalog → Tabela: db_vendas.raw_vendas
                ↓
4. Glue ETL Job → Transforma JSON para Parquet com particionamento
                ↓
5. S3 Parquet → (meu-dados-parquet-processado/vendas/ano=2024/mes=01/)
                ↓
6. Glue Data Catalog → Tabela: db_vendas.vendas_parquet
                ↓
7. Athena → Consultas SQL otimizadas com partições
```

---

## Conclusão

Você agora tem um pipeline completo de dados implementado na AWS usando apenas a interface administrativa! Este workflow:

✅ Ingere dados JSON
✅ Cataloga automaticamente
✅ Converte para Parquet (formato otimizado)
✅ Implementa particionamento para melhor performance
✅ Permite consultas SQL via Athena com baixo custo

**Próximos passos sugeridos:**
- Adicionar mais transformações no Glue (filtros, agregações)
- Integrar com QuickSight para visualizações
- Configurar alertas no CloudWatch
- Implementar política de retenção de dados

Se tiver dúvidas sobre qualquer etapa, me avise que posso detalhar mais!
