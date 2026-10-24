# Próximas funcionalidades

## Comandos e administração por mundo

- [x] Contexto de `gamerule`, seletores de entidades (`gamemode`, `give`, `kill` etc.) e `setworldspawn` por mundo; o console continua podendo operar globalmente.
- [x] Permissões do World Changer integradas à Permission API, compatíveis com LuckPerms: troca de mundo, administração e acesso contextual a cada mundo.
- [x] Comando administrativo `/wc delete` para descarregar e remover um mundo.
- [ ] Desativar a criação e o uso de portais do Nether e do End nos mundos criados pelo World Changer.

## Dados independentes

- [ ] Seed própria por mundo, preservando a geração normal, flat e void sem depender da seed global do overworld.
- [ ] Achievements/conquistas independentes por mundo.

## Importação de mundos

- [ ] Comando ou fluxo de importação de um mundo existente.
- [ ] Importar o mundo integralmente, preservando regiões, entidades, POI, estruturas, dados persistentes, gamerules, tempo, clima, spawn e inventários.
- [ ] Ler e manter exatamente a configuração de geração original do mundo importado: normal, flat, void ou qualquer geração customizada registrada pelos mods instalados.
- [ ] Validar mods, registros e dependências necessários antes de disponibilizar o mundo importado.

## Permissões ainda não cobertas

- [ ] Permissões LuckPerms específicas para comandos vanilla (`gamemode`, `give`, `gamerule` etc.), mantendo o filtro de dimensão já implementado.
- [ ] Criar um mod complementar separado, `WorldChanger WorldEdit`, para integrar as permissões do WorldEdit com o LuckPerms e permitir liberar ou negar comandos por jogador/grupo e por mundo, sem conceder OP.
