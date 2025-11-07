# Documentação: Prompts Customizados do GitHub Copilot no IntelliJ IDEA

## Visão Geral

O GitHub Copilot permite criar prompts customizados através de arquivos armazenados na pasta `.github/prompts` do seu projeto. Esses prompts funcionam como instruções reutilizáveis que orientam o Copilot a gerar código seguindo padrões específicos do seu projeto.

## Estrutura de Diretórios

```
seu-projeto/
└── .github/
    └── prompts/
        ├── java-service.md
        ├── test-unit.md
        ├── api-endpoint.md
        └── database-entity.md
```

## Configuração Inicial

### 1. Criar a Estrutura de Pastas

Crie a pasta `.github/prompts` na raiz do seu projeto:

```bash
mkdir -p .github/prompts
```

### 2. Criar Arquivos de Prompt

Cada prompt deve ser um arquivo Markdown (`.md`) com instruções claras. Exemplo de arquivo `java-service.md`:

```markdown
# Java Service Pattern

Crie um serviço Java seguindo estas diretrizes:

- Use anotação @Service do Spring
- Implemente injeção de dependência via construtor
- Adicione logs usando SLF4J
- Inclua tratamento de exceções
- Adicione validações de entrada
- Documente métodos públicos com JavaDoc
- Siga convenções de nomenclatura do projeto

Exemplo de estrutura:
```java
@Service
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public User findById(Long id) {
        log.debug("Buscando usuário com ID: {}", id);
        // implementação
    }
}
```
```

## Utilizando Prompts no IntelliJ IDEA

### Método 1: Via Chat do Copilot

1. **Abrir o Chat do Copilot**
   - Pressione `Ctrl + Shift + A` (Windows/Linux) ou `Cmd + Shift + A` (Mac)
   - Digite "Copilot Chat" e selecione

2. **Referenciar o Prompt**
   - No chat, digite `#file` e selecione o arquivo de prompt
   - Ou digite o caminho: `#.github/prompts/java-service.md`
   - Adicione sua solicitação específica

Exemplo:
```
#.github/prompts/java-service.md Crie um serviço para gerenciar produtos
```

### Método 2: Via Inline Chat

1. **Abrir Inline Chat**
   - Posicione o cursor onde deseja gerar código
   - Pressione `Ctrl + I` (Windows/Linux) ou `Cmd + I` (Mac)

2. **Referenciar o Prompt**
   - Digite `#file .github/prompts/java-service.md`
   - Adicione sua instrução específica

### Método 3: Via Comentários Contextuais

1. Adicione um comentário no código referenciando o prompt:
```java
// Seguir padrão: .github/prompts/java-service.md
// Copilot: criar serviço de autenticação
```

2. Pressione `Enter` e aguarde as sugestões do Copilot

## Boas Práticas para Criar Prompts

### Estrutura Recomendada

```markdown
# Título do Prompt

## Contexto
Breve descrição do propósito deste prompt.

## Diretrizes
- Lista de regras e padrões a seguir
- Convenções de nomenclatura
- Estrutura esperada

## Exemplo de Código
```language
// código exemplo
```

## Checklist
- [ ] Item 1 a verificar
- [ ] Item 2 a verificar
```

### Dicas Importantes

1. **Seja Específico**: Quanto mais detalhado o prompt, melhores os resultados
2. **Use Exemplos**: Inclua exemplos de código do seu projeto
3. **Mantenha Atualizado**: Revise e atualize prompts conforme o projeto evolui
4. **Organize por Contexto**: Crie prompts separados para diferentes necessidades
5. **Inclua Restrições**: Especifique o que NÃO fazer, além do que fazer

## Exemplos de Prompts Úteis

### Prompt para Testes Unitários

Arquivo: `.github/prompts/test-unit.md`

```markdown
# Padrão de Testes Unitários

Crie testes unitários seguindo:

- Use JUnit 5 e Mockito
- Padrão Given-When-Then
- Cobertura mínima: cenários felizes e de erro
- Nome descritivo: should_ReturnExpected_When_Condition
- Use @DisplayName para descrições legíveis
- Mock todas as dependências externas
- Assertions claras com mensagens

Estrutura:
```java
@Test
@DisplayName("Deve retornar usuário quando ID existe")
void should_ReturnUser_When_IdExists() {
    // Given
    Long userId = 1L;
    User expectedUser = new User(userId, "João");
    when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));
    
    // When
    User result = userService.findById(userId);
    
    // Then
    assertThat(result).isEqualTo(expectedUser);
    verify(userRepository).findById(userId);
}
```
```

### Prompt para API REST

Arquivo: `.github/prompts/api-endpoint.md`

```markdown
# API REST Endpoint Pattern

Crie endpoints REST seguindo:

- Use @RestController
- Mapeamento com @GetMapping, @PostMapping, etc.
- DTOs para request/response
- ResponseEntity para controle de status HTTP
- Validação com @Valid e Bean Validation
- Documentação com comentários JavaDoc
- Tratamento de exceções adequado

Estrutura:
```java
@RestController
@RequestMapping("/api/v1/users")
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        log.info("Criando novo usuário");
        User user = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(UserResponse.from(user));
    }
}
```
```

## Combinando Múltiplos Prompts

Você pode referenciar múltiplos prompts em uma única solicitação:

```
#.github/prompts/java-service.md #.github/prompts/test-unit.md 
Crie um serviço de pedidos com seus testes
```

## Troubleshooting

### Prompt Não Aparece nas Sugestões

- Verifique se o arquivo está em `.github/prompts/`
- Confirme que a extensão é `.md`
- Reinicie o IntelliJ IDEA se necessário
- Verifique se o GitHub Copilot está ativo

### Copilot Ignora as Instruções

- Seja mais específico no prompt
- Adicione mais exemplos concretos
- Use linguagem imperativa (faça X, não faça Y)
- Combine com instruções inline no chat

### Performance Lenta

- Reduza o tamanho dos prompts
- Evite prompts muito genéricos
- Use prompts específicos para cada contexto

## Versionamento de Prompts

Como os prompts ficam no repositório, você pode:

- Versionar junto com o código
- Fazer code review dos prompts
- Compartilhar padrões com a equipe
- Manter histórico de mudanças

## Conclusão

Prompts customizados permitem que o GitHub Copilot entenda melhor os padrões e convenções do seu projeto, gerando código mais consistente e alinhado com suas necessidades. Invista tempo criando bons prompts e sua produtividade aumentará significativamente.

---

**Versão**: 1.0  
**Última atualização**: Novembro 2025