# Segurança

Não envie credenciais em issues, commits, logs ou screenshots. A API REST local não possui autenticação e deve permanecer limitada ao loopback. Execute uma única instância da aplicação.

Segredos ficam no `.env`, que não deve ser versionado. `.runtime` pode conter banco, backups, estado OAuth e logs: mantenha essa pasta privada. `.gitignore` e `.dockerignore` excluem dados locais e credenciais do repositório/contexto de build. O exemplo `.env.example` contém apenas valores vazios ou placeholders.

Antes de publicar:

```powershell
node scripts/check-secrets.mjs
git add .
node scripts/check-secrets.mjs --staged
git diff --cached --check
```

O scanner compara arquivos publicáveis com os valores secretos do `.env` e alguns padrões conhecidos. Sua saída não imprime valores. Não detecta todo tipo de segredo e não substitui revisão manual. Não use `git add -f` para incluir arquivos ignorados.

Se uma credencial tiver sido compartilhada, revogue/rotacione no provedor e atualize o ambiente. Apagar a mensagem ou o arquivo não invalida a credencial. Revogar o segredo OAuth pode exigir nova autorização do usuário; não substitua credenciais em produção sem planejar essa atualização.

As URLs de coleta são limitadas a hosts das lojas, o webhook é validado como Discord HTTPS e menções estão desativadas. Respostas ambíguas do Discord não são reenviadas automaticamente. OAuth valida estado aleatório, temporário e de uso único. A renovação automática persistente do token Mercado Livre ainda está pendente.
