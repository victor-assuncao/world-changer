# World Changer

Mod de servidor para NeoForge 21.1.252 / Minecraft 1.21.1 (Java 21, mappings Mojang). Ele permite criar dimensões adicionais no servidor e alternar entre elas sem instalar conteúdo no cliente.

## Funcionalidades atuais

- Criar mundos `normal`, `flat` e `void`, listar e consultar informações.
- Trocar entre o overworld e os mundos gerenciados pelo mod.
- Salvar os dados do jogador separadamente por mundo, incluindo inventário e dados de mods armazenados nas attachments do jogador.
- Manter gamerules por mundo e aplicar contexto de mundo a comandos vanilla cobertos pelo mod.
- Controlar comandos do próprio mod com a Permission API do NeoForge, compatível com provedores como LuckPerms.
- Excluir um mundo com confirmação em duas etapas. Se arquivos estiverem bloqueados pelo sistema ou por outro mod, a limpeza é tentada na próxima inicialização.

Consulte [TODO.md](TODO.md) para funcionalidades planejadas. Como o mod manipula mundos e dados de jogadores, faça backup do servidor antes de testar alterações ou exclusões de mundos.

## Build

Use JDK 21 e importe este diretório como projeto Gradle no IntelliJ IDEA. O Gradle fará o download do NeoForge e das dependências na primeira sincronização.

Tarefas principais:

- `build` — compila e gera o JAR em `build/libs/`.
- `runServer` — inicia um servidor local de teste.
