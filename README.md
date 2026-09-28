# Milizé — Price Alert Bot

Bot pessoal em Java 21 e Spring Boot para descobrir descontos, acompanhar preços no PostgreSQL e enviar alertas ao Discord. Dados simulados existem somente nos testes.

## Integrações

| Loja | Situação validada |
| --- | --- |
| KaBuM | Coleta e entrega de ofertas reais no Discord confirmadas |
| Mercado Livre | Coleta de ofertas de catálogo e histórico confirmados; exige token válido |
| Amazon | Pendente — integração funcional não concluída; não coleta nem envia ofertas validadas |
| Pichau | Coletor implementado, mas acesso público retornou 403 no ambiente testado |

**Amazon ainda não está integrada funcionalmente.** Existe uma tentativa de leitura de páginas públicas no código, mas ela não foi validada como operacional. O acesso ao Amazon Associados/Creators API não foi habilitado, e o cliente dessa API não foi implementado. Portanto, Amazon não deve ser considerada uma loja suportada nesta versão; adicionar credenciais ao `.env` não basta para ativá-la.

Mercado Livre usa `/products/search`, `/products/{id}` e `/products/{id}/items`. A busca geral `/sites/MLB/search?q=...` retornou 403; não foi confirmada a política interna responsável. Catálogo não cobre todo o marketplace. Links diretos de anúncios ainda dependem de `/items/{id}`. **Renovação automática de tokens ainda não implementada:** ao renovar pelo OAuth oficial, salve os novos tokens no `.env` e recrie o serviço.

Consulte [requisitos de acesso](docs/ACESSO-LOJAS.md), [diagnóstico Mercado Livre](docs/MERCADO-LIVRE-DIAGNOSTICO.md) e [evidências datadas](VALIDATION.md). Registros antigos descrevem o estado naquela data.

## Iniciar

Requisitos: Docker Engine/Desktop com Compose. O build usa Java 21; não é necessário instalar Java no host.

```powershell
# Somente na primeira configuração; preserve um .env existente.
Copy-Item .env.example .env
# Configure DATABASE_PASSWORD e DISCORD_WEBHOOK_URL.
docker compose up -d --build
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/api/stores
```

API: `127.0.0.1:8080`. PostgreSQL: `127.0.0.1:5432`. Portas limitadas ao loopback, banco persistido em volume e migrações Flyway automáticas.

```powershell
docker compose logs -f price-alert-api
docker compose stop
docker compose up -d
# Após atualizar o .env:
docker compose up -d --no-build --no-deps price-alert-api
```

Mantenha Docker e computador ligados. Não use `docker compose down -v` para parar: isso remove o banco. Não execute os ambientes local e Docker nas mesmas portas. Alterar a senha no `.env` não altera a senha de um banco já inicializado.

## Discord

Crie um webhook em **Configurações do canal → Integrações → Webhooks** e coloque a URL em `DISCORD_WEBHOOK_URL`. Nome e avatar são os configurados no webhook.

A primeira oferta após iniciar o bot, ou após 15 minutos sem entregas confirmadas, inclui acima do cartão:

> Olá, soldados! Tudo bem? Encontrei novas promoções! 💜

Sem oferta elegível não é enviada saudação isolada. O cartão mostra loja, preço, referências, histórico disponível, cupom quando publicado e link. Menções estão desativadas. O envio usa `wait=true` para confirmar a entrega.

## Descoberta de descontos

A cada 15 minutos, uma rodada mistura **quatro termos**, intercalados entre as lojas. Se a rodada anterior estiver pendente, a próxima aguarda. Produtos rastreados têm prioridade; as consultas respeitam intervalo de 15 segundos, limites e bloqueios das lojas.

As 27 buscas padrão cobrem celulares, PS5/Xbox/Switch, jogos, gift cards, notebooks, periféricos e componentes de PC. Cada busca coleta até cinco produtos. Percorrer a lista exige aproximadamente sete rodadas (1h45 ou mais, conforme as lojas). É uma amostragem por relevância, não uma varredura completa ou classificação estrita de categorias. Não há ranking global de todas as ofertas.

A descoberta exige **25% de desconto** sobre a referência da loja ou, com histórico suficiente, sobre a média histórica. Um novo menor preço sozinho não ignora o mínimo. O desconto da loja é marcado como não confirmado pelo histórico quando faltam dados. Histórico suficiente exige três amostras e sete dias.

Regras de produtos explicitamente rastreados — preço-alvo, queda observada de 10%, reposição e novo cupom — continuam independentes. Produtos indisponíveis não geram alerta. Preço original ausente não é inventado. Frete, pagamento e regras dos cupons devem ser conferidos na loja.

## Configuração

| Variável | Padrão ou finalidade |
| --- | --- |
| DATABASE_URL | JDBC PostgreSQL; Compose usa o serviço `postgres` |
| DATABASE_USERNAME / DATABASE_PASSWORD | Credenciais do banco |
| DISCORD_WEBHOOK_URL | Segredo do webhook |
| MERCADO_LIVRE_ACCESS_TOKEN | Token oficial de acesso |
| MERCADO_LIVRE_CLIENT_ID | ID da aplicação OAuth |
| MERCADO_LIVRE_CLIENT_SECRET | Segredo da aplicação OAuth |
| MERCADO_LIVRE_REFRESH_TOKEN | Token de renovação |
| MERCADO_LIVRE_TOKEN_EXPIRES_AT | Validade do token |
| MERCADO_LIVRE_REDIRECT_URI | Retorno HTTPS registrado no DevCenter |
| MONITOR_QUERIES | Termos separados por vírgula |
| MONITOR_PROMOTION_INTERVAL | PT15M |
| MONITOR_TRACKED_INTERVAL | PT5M |
| MONITOR_REQUEST_GAP | PT15S |
| MONITOR_PROMOTIONS_ENABLED | true |
| MIN_STORE_DISCOUNT | 25 |
| MIN_HISTORICAL_DISCOUNT | 25 |
| ALERT_COOLDOWN | PT6H |

Opções adicionais em `src/main/resources/application.yml`.

## API local

| Método e rota | Função |
| --- | --- |
| GET `/api/products` | Produtos paginados |
| GET `/api/products/{id}` | Detalhes |
| GET `/api/products/{id}/history` | Histórico paginado |
| GET `/api/deals` | Ofertas recentes e disponíveis |
| GET/POST `/api/tracked-products` | Listar/cadastrar acompanhamento |
| DELETE `/api/tracked-products/{id}` | Remover acompanhamento |
| GET `/api/stores` | Diagnóstico por loja |
| GET `/api/status` | Configuração do Discord e lojas, sem segredos |
| GET `/actuator/health` | Saúde da aplicação |

Exemplo de corpo para cadastrar acompanhamento, substituindo a URL por um produto real:

```json
{"store":"KABUM","url":"https://www.kabum.com.br/produto/ID_REAL","targetPrice":500.00}
```

A API não tem autenticação e deve permanecer privada no loopback. Não publique sua porta na internet.

## Desenvolvimento e testes

JDK 21 recomendado. Para desenvolvimento sem Docker, configure PostgreSQL e execute `.\mvnw.cmd spring-boot:run`. Linux/macOS: `sh mvnw`. Os scripts opcionais `setup-local.ps1`, `start-local.ps1` e `stop-local.ps1` preparam um ambiente Windows em `.runtime`. Essa pasta pode conter banco e backups: não a apague indiscriminadamente.

```powershell
.\mvnw.cmd verify
# Banco temporário de teste, sem usar produção:
.\mvnw.cmd '-Dpostgres=true' verify
# Auxiliar OAuth: Node.js 24
node --test scripts/mercadolivre-oauth.test.mjs
node scripts/check-secrets.mjs
```

`verify` executa testes, Checkstyle e gera cobertura JaCoCo em `target/site/jacoco/index.html`. O lint básico verifica nomes de arquivos/tipos, equals/hashCode, instruções vazias e imports internos proibidos. Não há limite mínimo de cobertura configurado. O teste HTTP real é opt-in (`-Dpostgres=true -Dlive=true`), não envia ao Discord e depende da disponibilidade da loja.

O auxiliar OAuth escuta em `127.0.0.1:8765`, valida estado temporário de uso único e salva tokens localmente. `node scripts/mercadolivre-oauth.mjs authorize --manual` permite testar a troca manual. Exige retorno HTTPS registrado e em funcionamento. O Compose não abre túneis públicos automaticamente.

## Segurança e limitações

`.env`, variantes locais, `.runtime`, logs, backups, chaves e `target` não são versionados. `.env.example` não contém credenciais válidas. O scanner procura padrões de segredos e valores do `.env` nos arquivos publicáveis; não garante detectar todo tipo de segredo. Credenciais previamente compartilhadas devem ser revogadas/rotacionadas no provedor; removê-las de arquivos não as invalida.

Rode uma instância: filas de coleta não coordenam réplicas. A fila de alertas é persistida. Produto, preço e cupom evitam alertas repetidos; cooldown de seis horas tem exceções para eventos relevantes. Alertas antigos ou incompatíveis com preço/estoque atuais são descartados. Entregas ambíguas (`UNKNOWN` ou `SENDING`) exigem revisão e não são reenviadas automaticamente. Discord não oferece garantia de exatamente uma entrega em falhas de rede.

Arquitetura: agendador → filas de coleta → monitor → produto normalizado → transação de produto/histórico/análise/alerta → despachante Discord. Preços usam BigDecimal/NUMERIC. A aplicação não contorna autenticação, CAPTCHA ou bloqueios.
