# Registro de validação

## Steam, Epic Games e canais separados — 30/09/2026

- `mvnw -Dpostgres=true clean verify`: 126 testes, 125 aprovados, zero falhas/erros e um teste HTTP externo opt-in não executado. JaCoCo: 776/776 linhas, 772/772 caminhos condicionais e 5.977/5.977 instruções (100%); zero violações Checkstyle.
- Build Docker/Linux aprovado com as mesmas regras de cobertura; três testes PostgreSQL opt-in não executados nessa etapa. Auxiliar OAuth: três testes Node aprovados. Sintaxe de `health.js` validada e scanner de segredos sem achados.
- Testes cobrem normalização em reais, deduplicação, filtros de promoção ativa, preços inválidos, promoções futuras/expiradas, jogos gratuitos temporários, exclusividade de roteamento, webhook ausente e limites de envio independentes.
- Migração V3 testada no H2 e PostgreSQL; aceita preço zero, mantém preços existentes e rejeita preços negativos. Pipeline com Epic gratuita persiste produto, histórico e alerta sem duplicação.
- Backup local do PostgreSQL criado antes da atualização. Compose recriado preservando banco e volume OAuth. Migração V3 aplicada e `/actuator/health` retornou `UP`; `/api/status` confirmou os dois webhooks configurados. Nenhuma credencial é incluída neste registro.
- Primeira rodada real: cinco ofertas Steam e três Epic coletadas e persistidas. Os oito alertas ficaram `SENT` após confirmação do Discord (`wait=true`), exclusivamente pelo webhook de jogos. Limites locais preservados em 10%. Mensagem de boas-vindas adicional enviada ao canal de jogos a pedido do usuário, também confirmada pelo Discord.
- Na mesma rodada, KaBuM coletou cinco produtos; Mercado Livre e Amazon retornaram ausência de produtos reconhecíveis (`422`). Esses resultados não impediram o processamento das lojas de jogos e não significam que essas duas integrações tenham sido corrigidas nesta alteração.
- Limites: Steam monitora aplicativos do feed de ofertas em destaque; Epic monitora promoções ativas do feed `freeGamesPromotions`. Até cinco produtos por feed por rodada; não há cobertura integral dos catálogos. Ambas respeitam os percentuais mínimos do ambiente e usam exclusivamente o webhook de jogos.

## Cobertura completa — 29/09/2026

- `mvnw -Dpostgres=true clean verify`: 116 testes, 115 aprovados, zero falhas e um teste HTTP externo opt-in não executado. PostgreSQL temporário; nenhuma chamada real às lojas ou ao Discord.
- JaCoCo: 686/686 linhas, 673/673 caminhos condicionais e 5.312/5.312 instruções (100%). Nenhuma classe de produção excluída e nenhum código de produção alterado.
- `verify` agora exige 100% de linhas, branches e instruções; qualquer queda reprova o build. Zero violações Checkstyle.
- Novos cenários exercitam entradas inválidas, estoque, cupons, histórico, cooldown, respostas HTTP, filtros de catálogo, entrega ambígua e persistência OAuth em sistemas com e sem suporte a permissões POSIX.
- Estágio de build Docker/Linux também aprovado com a regra de 100%; os três testes da classe PostgreSQL são opt-in nesse comando. A execução completa com PostgreSQL foi validada no Windows.
- Cobertura total mede execução dos caminhos instrumentados, não garante ausência de defeitos nem disponibilidade das integrações externas.

## Página inicial e Swagger — 28/09/2026

- Página responsiva em `/`, com botão `Documentação` para `/swagger-ui/index.html`.
- Springdoc 2.8.17 integra OpenAPI em `/v3/api-docs`, limitado aos endpoints `/api/**`, com descrições e exemplos de acompanhamento.
- Teste HTTP com PostgreSQL valida página inicial, Swagger UI e caminhos da especificação. Maven: 79 testes, 78 aprovados e um externo opt-in não executado; zero violações Checkstyle e 100% das 686 linhas cobertas.

## GitHub Actions — 28/09/2026

- Workflow CI para push, pull request e execução manual, com permissões somente de leitura e actions oficiais fixadas por SHA.
- Verifica arquivos publicáveis, sintaxe JavaScript, auxiliar OAuth, Checkstyle, testes Java, PostgreSQL temporário e cobertura. Relatórios e JAR aprovado são armazenados por sete dias; não há deploy automático em servidor.
- Validação local antes do push: actionlint sem erros; Maven com PostgreSQL passou (77 testes aprovados e um HTTP externo opt-in não executado), zero violações Checkstyle, 100% das 686 linhas e 75,0% das decisões cobertas. Três testes Node aprovados e scanner de segredos sem achados.

## Renovação Java e persistência Docker — 28/09/2026

- Renovação migrada para Java: verificação a cada minuto e antes das consultas, antecedência de cinco minutos, token atualizado usado sem reiniciar o serviço. Em 401, uma renovação e uma repetição; erros 403 não são contornados.
- Tokens rotacionados salvos atomicamente no volume privado `oauth-data`. Client ID verificado ao carregar; estado persistido tem prioridade sobre o ambiente inicial. Linux: diretório 700 e arquivo 600, proprietário `bot`, verificados no container.
- Renovação real pela aplicação validada com `/users/me` HTTP 200. Container recriado mantendo o volume; tarefa Windows desativada e auxiliares de renovação externa removidos.
- `mvnw -Dpostgres=true clean verify`: 78 testes, 77 aprovados e um HTTP externo opt-in não executado. Zero violações Checkstyle; 686/686 linhas (100%) e 505/673 decisões (75,0%), sem exclusões.
- Testes incluem concorrência, restauração após reinício, rejeição de tokens, respostas inválidas, recuperação de falha de gravação e repetição da consulta após 401. Falhas de renovação têm espera de cinco minutos.
- Produção requer uma única instância, volume persistente protegido e credenciais iniciais válidas. Revogação da autorização ou perda dos tokens durante uma falha de persistência pode exigir novo login. Não depende de Node.js ou Agendador do Windows.

## Renovação automática OAuth — 28/09/2026

- Auxiliar Windows verifica validade a cada cinco minutos, renova com dez minutos de antecedência e persiste access/refresh token antes de recarregar o serviço Docker.
- Renovação real concluída; tarefa `Milize-MercadoLivre-Refresh` instalada e execução pelo Agendador terminou com código 0, sem renovar novamente um token vigente.
- Sete testes Node aprovados, incluindo rotação, preservação de configuração, rejeição de respostas inválidas e recuperação de falha ao recarregar Docker. Sintaxe Node validada e scanner de segredos sem achados.
- Código Java não alterado nesta etapa; a medição anterior de cobertura continua aplicável. A automação exige sessão Windows ativa e Docker disponível; revogação de autorização exige novo login.

## Cobertura e limpeza de lojas — 28/09/2026

- `mvnw -Dpostgres=true clean verify`: 70 testes, 69 aprovados, um teste HTTP externo opt-in não executado, nenhuma falha e zero violações Checkstyle.
- JaCoCo: 610/610 linhas (100%) e 458/615 decisões (74,5%), sem exclusão de classes. Execução de linhas não equivale a cobrir todas as combinações possíveis.
- Novos testes cobrem entrega e falhas do Discord, limitação de requisições, interrupção de espera, parsers, cupons, API e agendamento. A espera HTTP permite substituição nos testes, preservando o atraso real em produção.
- Removida a integração descontinuada do código, testes e documentação. Migração V2 remove produtos de lojas retiradas e seus históricos, cupons, alertas e acompanhamentos; testada com preservação das lojas mantidas.
- Backup privado do banco realizado antes da migração. O arquivo permanece em `.runtime`, excluído do Git.
- Nesta execução, mínimos localmente configurados em 10% para referência da loja e histórico. Três novas ofertas da KaBuM tiveram entrega confirmada pelo Discord. Mercado Livre retornou 401 e Amazon 422; essas falhas não foram apresentadas como coleta bem-sucedida.
- Três testes do auxiliar OAuth aprovados. Scanner de segredos sem achados nos arquivos publicáveis.

## Revisão para publicação — 28/09/2026

- README revisado: quatro termos por rodada, mínimo de 25%, saudação Discord, acesso por catálogo Mercado Livre e limitações reais de cada loja.
- Adicionados Checkstyle básico e JaCoCo ao `verify`, sem exclusão de classes de produção ou redução de limites existentes (não havia limite de cobertura).
- `mvnw -Dpostgres=true verify`: 39 testes, 38 aprovados e um teste HTTP externo opt-in não executado; PostgreSQL temporário validado; zero violações Checkstyle. Cobertura de linhas 78,9% e de branches 55,1%.
- Auxiliar OAuth: três testes Node aprovados.
- Scanner de segredos: nenhum segredo identificado nos arquivos publicáveis. Inspeção adicional de 217 arquivos de texto locais em `.runtime` e `target` não encontrou padrões de credenciais. Binários de banco/backups não foram inspecionados; são excluídos integralmente do Git. A inspeção não é garantia de ausência de segredos.
- Credenciais reais permanecem no `.env` ignorado. Credenciais já compartilhadas fora do repositório devem ser rotacionadas no provedor; essa revisão não as revoga.

## Mercado Livre: coleta real de catálogo — 28/09/2026

- Token renovado com sucesso; `/users/me` 200 e busca geral `/sites/MLB/search` 403, usando o mesmo token.
- Identificado e validado endpoint oficial `/products/{catalog_id}/items`, que retorna preços mesmo sem `buy_box_winner` no detalhe do catálogo.
- Coletor alterado para busca de catálogo → detalhes → ofertas. Links e histórico vinculados ao anúncio, exclusão de usados/compra mínima em quantidade, sem desconto inventado quando `original_price` está ausente.
- Corrigidos casos reais de permalink vazio e lista de ofertas 404. Negativas 401/403 continuam interrompendo a coleta.
- Build Docker: 36 testes, 34 aprovados e dois opcionais ignorados, sem falhas.
- Serviço implantado e `/api/stores` confirmou `MERCADO_LIVRE: OK`, cinco produtos persistidos em 28/09/2026 14:19:47 UTC. IDs locais 39–43, anúncios distintos do iPhone 15 128 GB azul, preços entre R$ 4.899,00 e R$ 5.572,00 no momento da consulta.
- Primeiras observações sem preço original: não há desconto confirmado nem validação de envio de alerta Mercado Livre nesta etapa. A coleta/histórico está validada; alerta exige uma queda real ou outro critério atendido.
- Limitações: cobertura de catálogo e primeira página de ofertas; renovação automática de tokens ainda pendente. A pesquisa geral continua negada. Detalhes em `docs/MERCADO-LIVRE-DIAGNOSTICO.md`.

## Investigação de acesso — 23/09/2026

- Usuário confirmou que não possui aplicação Mercado Livre nem acesso Amazon Associados/Creators API.
- Uma consulta pública de diagnóstico por loja confirmou: API Mercado Livre 403 `forbidden`; Amazon 503 com título “Amazon.com.br Algo deu errado”. Não foram usados proxies ou técnicas de contorno.
- Não há correção de seletor que extraia produtos dessas respostas. O acesso às duas lojas continua pendente; não se declara resolução funcional.
- `/api/stores` agora inclui descrição do erro e próximo passo. O cooldown conserva a causa 403, em vez de substituí-la indevidamente por 429.
- Página de erro conhecida da Amazon, inclusive se vier com HTTP 200, é classificada como indisponibilidade da loja, não como quebra do parser.
- Guia: `docs/ACESSO-LOJAS.md`. Verificador de token Mercado Livre: `scripts/check-mercadolivre.ps1` (somente leitura, sem impressão do segredo; depende de token real).
- Build Docker: 29 testes aprovados, 2 testes PostgreSQL opt-in não executados, zero falhas/erros.

## Estado atual — Docker, 23/09/2026

- Docker Desktop ativo após reinicialização; imagem do bot construída com sucesso, incluindo os testes do Dockerfile.
- Backup PostgreSQL criado em `.runtime/backups/before-docker-20260923-102326.dump`.
- Migração verificada: 5 produtos, 5 registros de histórico, 5 alertas, todos os 5 com status SENT; nenhum acompanhado ou cupom.
- PostgreSQL no volume `price-alert-bot_postgres-data`, saudável. Aplicação em http://localhost:8080, com `/actuator/health` retornando UP e Discord configurado.
- Banco local original preservado em `.runtime/postgres-data` e parado. Agora os dados ativos ficam no volume Docker; não iniciar a instalação local simultaneamente.
- Limite solicitado pelo usuário mantido em 10%. Registro de alertas enviados restaurado para impedir reenvio dos mesmos produtos/preços.
- Primeira coleta no Docker confirmou 5 produtos da KaBuM e encontrou um produto novo. O alerta 6 foi enviado e confirmado pelo Discord às 10:25 de 23/09/2026, sem repetir os cinco alertas antigos.
- Build Docker: 28 testes aprovados e 2 testes PostgreSQL opt-in não executados, zero falhas/erros.

As notas abaixo registram as etapas anteriores e as limitações das lojas.

Data: 22/09/2026. Ambiente: Windows, inicialmente com JDK 26.0.1; Java 21 portátil instalado posteriormente. Docker Desktop 4.91.0, Docker Engine CLI 29.8.0, Compose 5.5.1 e WSL 2.7.13 foram instalados. O Windows requer reinicialização para concluir a ativação dos componentes WSL/Virtual Machine Platform; o backend Docker ainda não foi validado.

## Execução local configurada

Atualização: a pedido do usuário, `MIN_STORE_DISCOUNT` foi reduzido de 40 para 10 no `.env` para visualizar alertas. Cinco ofertas reais da KaBuM foram enviadas pelo cliente Java e confirmadas pelo Discord (`alerts` 1 a 5, estado `SENT`, às 21:14 de 22/09/2026).

O primeiro envio falhou por resolução DNS do Discord no cliente Netty. O cliente passou a usar o resolvedor padrão do sistema. Antes de recolocar os cinco alertas na fila, o usuário confirmou que não havia recebido nenhuma mensagem. Os oito testes de HTTP/Discord passaram com Java 21.

- Java Temurin 21.0.12.1 e PostgreSQL 17.11 instalados de forma portátil em `.runtime`, com verificação SHA-256 dos downloads.
- Bot iniciado em segundo plano em http://localhost:8080, com o webhook do usuário configurado.
- PostgreSQL persistente em `.runtime/postgres-data`, restrito a 127.0.0.1:5432 e autenticado com senha SCRAM.
- O agendador coletou e gravou cinco produtos reais da KaBuM. Nesta rodada, os descontos anunciados foram de aproximadamente 10% a 15%; nenhum alerta de 40% foi forçado.
- Parada e reinicialização verificadas: os cinco produtos permaneceram no banco. Repetir o comando de início não criou outra instância.
- Codificação UTF-8 dos nomes dos produtos conferida na API.
- O bot foi deixado ativo após a verificação. Para reiniciar depois de desligar o computador: `scripts/start-local.ps1`. Não foi instalado serviço de inicialização automática do Windows.
- Busca `teclado` adicionada no início de `MONITOR_QUERIES`, preservando as demais categorias.

## Evidência real

- Maven compilou a aplicação.
- Verificação final `-Dpostgres=true verify`: 29 testes aprovados, zero falhas/erros; um teste HTTP opt-in não repetido nessa rodada. Esse teste público havia passado na execução anterior com `-Dlive=true`.
- JAR executável gerado em `target/price-alert-bot-0.1.0.jar`.
- Spring Boot iniciou com servidor HTTP e PostgreSQL 14.22 temporário real.
- Flyway aplicou V1 e Hibernate validou o esquema.
- GET /actuator/health retornou UP; GET /api/products retornou os dados persistidos.
- O coletor Java da KaBuM consultou https://www.kabum.com.br/busca/teclado pelo cliente HTTP de produção.
- Cinco produtos reais foram normalizados, analisados e persistidos com histórico. Exemplos de IDs: 93160, 538689, 506048, 416203. Os preços são observações transitórias; este arquivo não anuncia ofertas.
- PostgreSQL foi encerrado ao final do teste. O teste não deixa um serviço de produção ativo.

## Restrições observadas

- Mercado Livre: API sem token e busca pública retornaram 403. Não houve tentativa de superar o bloqueio.
- Amazon: ferramentas HTTP tiveram respostas inconsistentes, incluindo 503; não foi possível confirmar coleta de produto.
- KaBuM: a busca genérica por ofertas redirecionou para uma página sem produtos; a busca por teclado retornou dados reais utilizáveis.
- Webhook Discord configurado pelo usuário. Uma mensagem de teste real foi enviada por `scripts/test-discord.ps1`, com `wait=true`, e o Discord confirmou o recebimento. O fluxo completo de uma oferta real até o canal ainda não foi confirmado.
- Configuração Compose validada com `docker compose config --quiet`. Os contêineres ainda não foram iniciados: a ativação dos componentes Windows depende de reinicialização.

## Como reproduzir

```powershell
.\mvnw.cmd -Dpostgres=true -Dlive=true test
```

Relatórios automatizados em `target/surefire-reports`. Respostas brutas e logs locais de diagnóstico ficam em `.runtime`, ignorado pelo Git.

O fluxo KaBuM → HTTP real → PostgreSQL → análise → Discord foi confirmado com o limite de teste de 10%. Isso não comprova funcionamento das outras três lojas nem execução com Docker. O projeto não é declarado completamente validado para todas as integrações.

