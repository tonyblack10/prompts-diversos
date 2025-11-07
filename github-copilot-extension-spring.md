# Criando uma GitHub Copilot Extension com Spring

Vou mostrar os pontos essenciais para criar uma extensão do GitHub Copilot usando Spring Boot.

## 1. Estrutura do Projeto

```
copilot-extension/
├── src/main/java/com/example/copilot/
│   ├── CopilotExtensionApplication.java
│   ├── controller/AgentController.java
│   ├── model/CopilotRequest.java
│   └── service/AgentService.java
├── pom.xml
└── agent.json
```

## 2. Dependencies (pom.xml)

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

## 3. Controller Principal

```java
@RestController
@RequestMapping("/agent")
public class AgentController {
    
    @Autowired
    private AgentService agentService;
    
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> handleCopilotRequest(
            @RequestBody CopilotRequest request,
            @RequestHeader("X-GitHub-Token") String token) {
        
        return agentService.processRequest(request, token);
    }
}
```

## 4. Model de Request

```java
public class CopilotRequest {
    private List<Message> messages;
    private String model;
    
    public static class Message {
        private String role;
        private String content;
        // getters/setters
    }
}
```

## 5. Service com Lógica da IA

```java
@Service
public class AgentService {
    
    public Flux<String> processRequest(CopilotRequest request, String token) {
        return Flux.create(sink -> {
            try {
                // Extrair a mensagem do usuário
                String userMessage = request.getMessages().stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(Message::getContent)
                    .findFirst()
                    .orElse("");
                
                // Processar e gerar resposta
                String response = generateResponse(userMessage);
                
                // Enviar em formato SSE (Server-Sent Events)
                sink.next("data: " + toJson(response) + "\n\n");
                sink.complete();
                
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
    
    private String generateResponse(String userMessage) {
        // Sua lógica de IA aqui
        // Pode chamar APIs externas, processar dados, etc.
        return "Resposta processada para: " + userMessage;
    }
}
```

## 6. Manifest (agent.json)

```json
{
  "name": "minha-extensao",
  "description": "Extensão customizada para Copilot",
  "agent": {
    "type": "agent",
    "url": "https://sua-url.com/agent"
  },
  "skills": [
    {
      "name": "processar-codigo",
      "description": "Processa e analisa código"
    }
  ]
}
```

## 7. Application Properties

```properties
server.port=8080
spring.application.name=copilot-extension
```

## Pontos Críticos:

1. **Autenticação**: Valide o token do GitHub no header `X-GitHub-Token`
2. **Streaming**: Use `Flux` ou SSE para respostas em tempo real
3. **Rate Limiting**: Implemente controle de requisições
4. **CORS**: Configure adequadamente para aceitar requisições do GitHub

## Deploy:

A extensão precisa ser hospedada publicamente (HTTPS obrigatório). Opções:
- Railway
- Heroku
- Azure App Service
- AWS Elastic Beanstalk

Depois registre a extensão no GitHub através do GitHub Marketplace para desenvolvedores.

**Quer que eu detalhe algum desses pontos específicos ou mostre como integrar com APIs de IA como OpenAI/Anthropic?**
