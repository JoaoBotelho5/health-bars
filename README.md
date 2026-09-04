# HealthBar
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-blue)
![Side](https://img.shields.io/badge/side-client--only-lightgrey)

CurseForge Link: https://www.curseforge.com/minecraft/mc-mods/healthbars
Mod client-side para Minecraft (NeoForge) que mostra uma barra de vida sobreposta ao ecrã quando o jogador aponta para uma entidade — incluindo através de blocos transparentes (vidro, água, folhagem). Projeto pequeno e focado, mas com problemas de engenharia reais por trás de uma feature aparentemente simples.

## O que este projeto demonstra

| Área | Onde está aplicado |
|---|---|
| **Algoritmos geométricos custom** | `isOccluded()` implementa um ray-march manual (`ClipContext`) sobre a linha de visão do jogador, avançando ponto a ponto para distinguir blocos sólidos de blocos transparentes — não usa o raycast nativo diretamente, porque este não ignora vidro/água/folhagem por defeito. |
| **Gestão de estado com tolerância temporal** | Em vez de a barra desaparecer no instante em que o cursor sai do alvo, um buffer de 500 ms (`lastSeenTime`/`BUFFER_MS`) mantém a última entidade visada em cache — evita "flicker" da UI num caso de uso de rendering em tempo real. |
| **Interpolação e animação** | Barra de vida suavizada por `lerp()` por-frame (em vez de saltar instantaneamente entre valores), gradiente de cor calculado dinamicamente (verde → vermelho) e um efeito de pulsação baseado em função seno para vida crítica. |
| **Programação defensiva** | Resolução do nome da entidade com múltiplos fallbacks (`hasCustomName` → `getDisplayName` → `getName`), incluindo tratamento de nulos e remoção de formatação de texto. |
| **Separação client/server em mods** | `ModClient` está isolado num `@Mod(dist = Dist.CLIENT)` próprio — garante que código de rendering nunca é carregado num servidor dedicado, uma distinção arquitetural específica (mas transferível) de aplicações com múltiplos targets de deployment. |

## Como funciona

1. `RenderGuiEvent.Post` é intercetado a cada frame para desenhar diretamente sobre o HUD via `GuiGraphics`.
2. O `HitResult` atual do jogador é inspecionado: se for uma entidade, essa é o alvo.
3. Se for um bloco transparente (vidro, água, plantas), o mod faz uma pesquisa adicional — recolhe todas as `LivingEntity` na `AABB` entre o jogador e o ponto de impacto, e testa cada uma com `isOccluded()` para confirmar que não há nenhum bloco sólido a bloquear a visão real.
4. A vida do alvo é suavizada (`lerp`) e desenhada como uma barra com gradiente de cor, nome, valores absolutos e percentagem — com um efeito de aviso pulsante abaixo dos 20% de vida.

## Estrutura do projeto

```
com.healthbar.healthbarmod
├── healthbar.java        → entrypoint comum (@Mod), define o MODID
├── ModClient.java         → entrypoint client-only, regista o ecrã de configurações
└── healthbargui.java      → toda a lógica de deteção de alvo, raycasting e rendering do HUD
```

## Instalação

1. Clonar este repositório.
2. Abrir em IntelliJ IDEA ou Eclipse.
3. Correr `gradlew --refresh-dependencies` se faltarem bibliotecas, ou `gradlew clean` para reiniciar o ambiente de build.

## Mapping names

Este projeto usa as mappings oficiais da Mojang para métodos e campos. Ver os termos de licença em:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Recursos

- Docs do NeoForged: https://docs.neoforged.net/
- Discord do NeoForged: https://discord.neoforged.net/
