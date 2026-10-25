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

## Importar um mundo existente

Execute como administrador (permissão `worldchanger.import`, padrão OP nível 2):

```text
/wc import aeronoia-criativo
/wc list
/wc switch aventura
```

Coloque o save em `worlds/<nome>` na raiz do servidor (ao lado de `server.properties`). No ambiente Gradle deste projeto, use `run/worlds/<nome>`. A pasta `worlds` é criada automaticamente ao iniciar o servidor. Por exemplo, `/wc import aeronoia-criativo` importa exclusivamente `run/worlds/aeronoia-criativo` em desenvolvimento. O comando aceita apenas o nome do mundo, com letras minúsculas, números, `_` e `-` (até 32 caracteres), sem argumento de diretório personalizado.

A pasta deve ser um save completo, com `level.dat` e `session.lock`, fechado pelo servidor de origem, ou um backup consistente feito com ele parado. A importação adquire o lock existente sem escrever na origem; não use uma cópia feita enquanto o servidor de origem escrevia os arquivos. O argumento aceita apenas o nome de uma pasta diretamente dentro de `worlds`; caminhos absolutos, subpastas, `..` e links simbólicos são recusados.

O save precisa estar na mesma versão de dados do Minecraft. Converta versões antigas usando uma cópia antes de importar. Instale previamente os mods que fornecem conteúdo e geração utilizados pelo mundo e os datapacks habilitados no servidor de destino, reiniciando-o para carregar os registros. A lista histórica `fml.LoadingModList` apenas gera avisos para mods ausentes: ela não declara dependências e pode incluir utilitários removidos, como um antigo gerenciador de mundos. A validação dos codecs de geração continua bloqueando referências não resolvidas a geradores, biomas, configurações de ruído e tipos de dimensão. Essa validação não verifica todos os blocos e entidades dos chunks; preserve os mods de conteúdo necessários. Todos os mundos usam os mods, datapacks e registros ativos do mesmo servidor. A lista histórica `DataPacks.Enabled` gera avisos quando os IDs antigos não estão ativos; não exige nomes de arquivo ou cópias idênticas por mundo. Datapacks arquivados na pasta importada não são ativados separadamente. Caso a geração dependa de registros ausentes, instale o datapack correspondente no servidor e reinicie antes de importar. Texturas e shaders seguem os recursos/configurações do cliente e o resource pack do servidor, quando configurado; o World Changer não instala nem força shaders nos clientes. Falhas ao resolver registros ou geradores cancelam a operação, sem substituir o gerador por um preset padrão.

A pasta inteira é copiada para `dimensions/worldchanger/<nome>`: regiões, entidades, POI, estruturas em chunks, dados e datapacks permanecem na cópia. O mundo acessado por `/wc switch` corresponde ao overworld do save; Nether, End e dimensões adicionais ficam preservados nos arquivos copiados, mas não são registrados como destinos separados. Seed, gerador, preset flat e opção de estruturas vêm do `level.dat` importado e são relidos a cada inicialização. Spawn, relógio, clima e gamerules são próprios do mundo importado. Dados de jogador do save ficam arquivados; o sistema de inventário por mundo continua sendo o do World Changer.

A importação registra uma transação antes de copiar e só a conclui após carregar o mundo. Em caso de falha, descarrega e remove a cópia. Se a limpeza falhar ou o processo for interrompido, reinicie o servidor: ele remove os arquivos da transação incompleta antes de permitir repetir o nome. A origem nunca é movida ou removida. Mundos já importados que deixarem de carregar por dependências ausentes conservam o registro; restaure as dependências e reinicie.

### Validação manual em servidor de teste

1. Importe um save fechado com construções, entidades e POI; compare os dados após `/wc switch`, salve e reinicie.
2. Use um save com seed diferente e um flat personalizado; explore chunks novos e compare com a mesma origem. Repita com gerador de mod e datapack.
3. Tente importar nome duplicado, `level.dat` ausente, versão incompatível, registro de geração ausente e origem aberta: devem falhar com causa explícita. Um mod utilitário ou datapack de receitas apenas no histórico deve gerar aviso, usando os recursos ativos compartilhados.
4. Interrompa o processo durante a cópia; reinicie e repita a importação após a limpeza. Compare a origem com seu backup para confirmar que permaneceu intacta.

## Compilar

Use JDK 21 e importe este diretório como projeto Gradle no IntelliJ IDEA. O Gradle fará o download do NeoForge e das dependências na primeira sincronização.

Tarefas principais:

- `build` — compila e gera o JAR em `build/libs/`.
- `runServer` — inicia um servidor local de teste.
