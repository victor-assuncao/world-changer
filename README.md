# World Changer

**Vários mundos, um só servidor.** Crie um mundo de sobrevivência, um espaço de construções ou um mapa vazio e alterne entre eles sem sair do servidor.

O World Changer é um mod de servidor para **Minecraft 1.21.1 com NeoForge**. Cada mundo gerenciado pode ter sua própria seed, inventário dos jogadores, conquistas, estatísticas e regras de jogo. Também é possível importar mapas existentes e controlar comandos por mundo usando LuckPerms.

| Compatibilidade | Requisito |
| --- | --- |
| Minecraft | **1.21.1** |
| Carregador | **NeoForge 21.1.252 ou superior na linha 21.1** |
| Java | **21** |
| Instalação | No servidor; este mod não precisa ser instalado no cliente |
| LuckPerms | Opcional, para configurar permissões por jogador ou grupo |
| Licença | [MIT](LICENSE) |

> Outros mods do seu servidor podem exigir instalação no cliente. O World Changer não substitui essas dependências.

## O que você pode fazer

- **Criar mundos** normais, planos ou vazios, com seed opcional.
- **Importar mapas** existentes sem mover ou modificar a pasta de origem.
- **Trocar de mundo** por comando e retornar ao overworld principal.
- **Separar o progresso dos jogadores:** inventário, experiência, modo de jogo, conquistas e estatísticas são restaurados ao voltar a cada mundo gerenciado.
- **Configurar regras por mundo**, incluindo gamerules, spawn, horário e clima.
- **Liberar comandos sem OP**, com permissões individuais e contexto de mundo no LuckPerms.

## Instalação

1. Prepare um servidor **NeoForge para Minecraft 1.21.1**, com Java 21.
2. Pare o servidor e coloque `worldchanger-1.0.0.jar` na pasta `mods`.
3. Se estiver atualizando, remova o JAR antigo do World Changer para manter apenas uma versão instalada.
4. Inicie o servidor e use `/wc help` para consultar os comandos.

Faça um backup do save antes de instalar ou atualizar o mod e antes de excluir mundos. Na instalação padrão, comandos administrativos exigem **OP nível 2**; jogadores podem consultar a ajuda, listar mundos e trocar de mundo.

## Primeiros passos

Como administrador, crie seus mundos:

```mcfunction
/wc create aventura normal 123456
/wc create construcoes flat
/wc create vazio void
```

Depois, entre em um deles:

```mcfunction
/wc switch aventura
```

Para voltar ao mundo principal:

```mcfunction
/wc switch overworld
```

Os nomes aceitam letras minúsculas, números, `_` e `-`, com até 32 caracteres. A seed é opcional: quando omitida, o mod sorteia uma para o novo mundo.

### Tipos de mundo

| Tipo | Como é o mundo |
| --- | --- |
| `normal` | Geração normal de overworld, com seed própria |
| `flat` | Mundo superplano com as camadas padrão do Minecraft |
| `void` | Mundo vazio com uma plataforma inicial |

## Comandos

Use os comandos abaixo no chat. No console do servidor, omita a `/` inicial.

| Comando | O que faz | Acesso padrão |
| --- | --- | --- |
| `/wc help` | Mostra a ajuda | Todos |
| `/wc list` | Lista os mundos | Todos |
| `/wc info [nome]` | Mostra informações do mundo atual ou indicado | Todos; a seed aparece para OP nível 2 |
| `/wc switch <nome>` | Entra em um mundo; use `overworld` para voltar | Todos |
| `/wc create <nome> [tipo] [seed]` | Cria um mundo; o tipo padrão é `normal` | OP nível 2 |
| `/wc import <nome>` | Importa um mapa da pasta `worlds` | OP nível 2 |
| `/wc setspawn <mundo> <x> <y> <z>` | Define o spawn de um mundo | OP nível 2 |
| `/wc delete <nome>` | Solicita a exclusão de um mundo | OP nível 2 |
| `/wc confirm` | Confirma a exclusão solicitada | OP nível 2 |

Para informar uma seed, informe também o tipo: `/wc create aventura normal 123456`.

### Excluir um mundo

```mcfunction
/wc delete aventura
/wc confirm
```

Execute a confirmação **em até 15 segundos**, usando o mesmo jogador ou console que solicitou a exclusão. A exclusão remove os arquivos do mundo; mantenha um backup se quiser recuperá-lo. Arquivos que estiverem bloqueados podem ter sua remoção concluída na próxima inicialização.

## Importar um mapa existente

1. Pare o servidor de origem ou use um backup consistente feito com ele parado.
2. Copie o save completo para `worlds/<nome>`, ao lado de `server.properties` no servidor de destino.
3. Confira se o mapa está na versão de dados do Minecraft 1.21.1 e instale seus mods e datapacks necessários no servidor de destino.
4. Com o servidor de destino iniciado, importe e entre no mapa:

```mcfunction
/wc import meu_mapa
/wc switch meu_mapa
```

Exemplo de organização antes da importação:

```text
servidor/
├── mods/
├── worlds/
│   └── meu_mapa/
│       ├── level.dat
│       ├── session.lock
│       └── region/
└── server.properties
```

O mod cria uma cópia do **overworld do mapa**; a pasta de origem permanece intacta. O Nether e o End da origem não são importados como mundos separados. A seed, o gerador, o spawn e as regras do mapa são preservados na importação. Os dados de jogador da origem não substituem os perfis por mundo gerenciados pelo World Changer.

Todos os mundos compartilham os mods e datapacks ativos no servidor. Datapacks dentro do save importado não são ativados separadamente. Se a geração depender de conteúdo ausente, instale as dependências e reinicie antes de tentar importar novamente. Texturas e shaders continuam sendo configurados no cliente.

## Permissões com LuckPerms

O LuckPerms é **opcional**. Sem ele, valem os acessos padrão da tabela de comandos e os níveis OP originais dos comandos vanilla.

Para usá-lo, instale a versão NeoForge do LuckPerms, inicie o servidor para gerar a configuração e depois pare-o. Em `config/neoforge-server.toml`, configure:

```toml
permissionHandler = "luckperms:permission_handler"
```

Reinicie o servidor após a alteração. Execute os exemplos a seguir como administrador e substitua `Jogador` pelo nome desejado.

### Comandos do World Changer

| Permissão | Ação | Padrão |
| --- | --- | --- |
| `worldchanger.help` | Consultar ajuda | Permitido |
| `worldchanger.list` | Listar mundos | Permitido |
| `worldchanger.info` | Consultar informações | Permitido |
| `worldchanger.switch` | Trocar de mundo | Permitido |
| `worldchanger.world` | Acessar um mundo de destino | Permitido |
| `worldchanger.create` | Criar mundos | OP nível 2 |
| `worldchanger.import` | Importar mapas | OP nível 2 |
| `worldchanger.setspawn` | Definir spawn | OP nível 2 |
| `worldchanger.delete` | Solicitar e confirmar exclusão | OP nível 2 |

Por exemplo, para impedir que um jogador entre em `construcoes`:

```text
/lp user Jogador permission set worldchanger.world false world=worldchanger:construcoes
```

### Liberar gamemode em apenas um mundo

Cada comando vanilla tem seu próprio nó: **`worldchanger.vanilla.<comando>`**. Exemplos: `worldchanger.vanilla.gamemode`, `worldchanger.vanilla.give` e `worldchanger.vanilla.gamerule`.

Para permitir `/gamemode` sem OP somente no mundo chamado `construcoes`:

```text
/lp user Jogador permission set worldchanger.vanilla.gamemode true world=worldchanger:construcoes
```

Dentro desse mundo, o jogador poderá usar `/gamemode creative` ou `/gamemode survival`. Essa concessão não libera o comando em outros mundos.

Para um grupo inteiro:

```text
/lp group construtores permission set worldchanger.vanilla.gamemode true world=worldchanger:construcoes
/lp user Jogador parent add construtores
```

Para negar outro comando no mesmo mundo, independentemente da concessão de gamemode:

```text
/lp group construtores permission set worldchanger.vanilla.give false world=worldchanger:construcoes
```

### Como escrever o contexto de mundo

O contexto é **`world`**, seguido pelo identificador completo da dimensão. Use exatamente o nome mostrado por `/wc info`.

| Mundo | Contexto |
| --- | --- |
| Mundo criado ou importado chamado `construcoes` | `world=worldchanger:construcoes` |
| Mundo chamado `aventura` | `world=worldchanger:aventura` |
| Overworld principal | `world=minecraft:overworld` |
| Nether vanilla | `world=minecraft:the_nether` |
| End vanilla | `world=minecraft:the_end` |

O nome é o nome do mundo, não o tipo de geração: um mundo do tipo `flat` chamado `construcoes` usa `worldchanger:construcoes`.

- Sem uma permissão configurada, comandos administrativos mantêm seus níveis OP originais.
- Uma negação explícita bloqueia o comando também para OPs. Herança, grupos e curingas seguem a resolução do LuckPerms.
- Concessões globais e outras permissões herdadas ainda podem dar acesso em outros mundos; remova-as se quiser restringir o jogador.
- Aliases usam o nó do destino: `tp` → `teleport`, `xp` → `experience`, `tell`/`w` → `msg` e `tm` → `teammsg`.
- Seletores como `@s` e `@a` exigem também `neoforge.use_entity_selectors` para jogadores sem OP. Usar o nome do jogador como alvo não exige esse nó.
- A autorização considera o mundo atual do jogador. `/execute` verifica a permissão do comando de destino; liberar `execute` não contorna uma negação de `give`. A capacidade vanilla de escolher uma dimensão com `execute in` é mantida.

## O que é separado entre os mundos?

Os mundos gerenciados possuem dados próprios dos jogadores, conquistas, recompensas de conquistas e estatísticas. Ao sair e voltar, o mod restaura o progresso daquele mundo. Dados de mods armazenados nas attachments do jogador também são incluídos; isso não significa que todos os sistemas de qualquer mod tenham isolamento próprio.

O **overworld, Nether e End vanilla continuam compartilhando o progresso original**. Não é possível dividir retroativamente as conquistas ou estatísticas antigas entre os mundos novos.

Portais do Nether e do End não funcionam nos mundos World Changer, incluindo mapas importados. Olhos do End ainda podem ser colocados nas molduras como decoração. Para trocar de mundo, use `/wc switch`.

Gamerules dos mundos gerenciados são próprias. Os filtros de alvos dos comandos limitam jogadores a entidades da dimensão usada pela fonte do comando; console e fontes sem jogador mantêm o comportamento global vanilla. A permissão de um comando controla seu acesso, sem transformar todos os comandos vanilla em operações locais: comandos de administração do servidor continuam tendo seu efeito original.

## Dúvidas comuns

**O comando aparece como desconhecido?** Confira a instalação, a permissão e se o servidor foi reiniciado após atualizar o JAR. Comandos sem permissão podem ficar ocultos no Minecraft.

**A permissão por mundo não funcionou?** Use `/wc info` para conferir o identificador exato. Verifique também se o provedor LuckPerms está ativo e se não há uma negação ou concessão herdada afetando o jogador.

**O mundo não aparece?** Use `/wc list`. Para entrar em um mapa colocado em `worlds`, primeiro execute `/wc import <nome>`.

**A importação falhou?** Confira o nome da pasta, os arquivos do save, a versão do Minecraft e as dependências indicadas no console. Se uma importação foi interrompida, reinicie para concluir a limpeza antes de repetir.

## Compilar a partir do código

Com JDK 21 instalado, execute na pasta do projeto:

```powershell
.\gradlew.bat build
```

No Linux ou macOS, use `./gradlew build`. O arquivo gerado fica em **`build/libs/worldchanger-1.0.0.jar`**. A primeira compilação precisa de internet para baixar as dependências.

Para desenvolver, `runServer` inicia o servidor local na pasta `run`. Não mantenha uma cópia do próprio World Changer em `run/mods` nesse modo: o Gradle já carrega o código do projeto. Os mundos e configurações desse servidor local não fazem parte do JAR distribuído.
