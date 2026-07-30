# RLCraft Enchantment Recipes

A Minecraft **1.12.2** Forge mod that adds crafting recipes for **max-level enchanted books**, built
around RLCraft's material progression. One recipe per enchantment, always at its highest level — so
endgame enchants become a crafting project instead of an endless villager grind.

Every recipe is visible in **JEI**, which is the intended way to browse what's craftable.

> Unofficial addon. Not affiliated with or endorsed by the RLCraft or RLCraft Dregora teams.

---

## ⚠️ Set `PACK_MODE` first

Recipes were authored for **RLCraft Dregora v1.1.2b**. Base **RLCraft 2.9.3** is also supported, but
you have to tell the mod which one you're playing:

| Your pack | Set `PACK_MODE` to |
|---|---|
| RLCraft Dregora | `dregora` (default) |
| RLCraft | `rlcraft` |

**If this is wrong, most recipes silently won't appear.** The two packs ship different versions of
SoManyEnchantments, which renamed most of its enchantments between them.

---

## Requirements

- Minecraft **1.12.2**, Forge **14.23.5.2847+**
- RLCraft Dregora v1.1.2b or RLCraft 2.9.3
- **JEI** — optional, but you'll want it

No hard dependencies. Missing content never crashes the game; the affected recipe just doesn't
register.

---

## Configuration

`config/rlcraftenchantrecipes.cfg`, or in-game via **Mods → RLCraft Enchantment Recipes → Config**.

### `PACK_MODE` — `dregora` (default) or `rlcraft`
See above. On `rlcraft` a few ingredients change, because base RLCraft doesn't have the originals:

| Recipe asks for | You use instead |
|---|---|
| Ice and Fire copper ingot | Silver ingot |
| Ice and Fire hydra heart | Fire or ice dragon heart |
| Ice and Fire hydra fang | Sea serpent fang |
| Spartan Weaponry iron scythe | Iron hoe |
| Curseweave Fabric | That curse's own material (rotten flesh, bone, …) |
| Prismarine block | Prismarine shard |
| Sea lantern | Prismarine crystals |

Recipes accepting "any elemental dragonbone weapon" take fire or ice only. Ten enchantments don't
exist in base RLCraft and have no recipe there: Ascetic, Breached Plating, Combat Medic, Extinguish,
and the subjects Biology, Chemistry, Geography, History, Mathematics and Physics. **Subject English**
and **Subject Science** are added instead, since only base RLCraft has them.

*Restart the game after changing this.*

### `XP_TOME_LEVEL` — `0`–`30`, default `30`
How full an XP Tome has to be for tome recipes. A fuller tome than required is fine; an emptier one
is rejected. Only multiples of 5 count — anything else rounds down. Takes effect immediately.

### `CONSUME_XP_BOOK_ON_CRAFT` — default `false`
When `false`, a full XP Tome is **drained rather than destroyed** — you get an empty tome back, like
a bucket. When `true`, it's consumed outright.

Recipes that specifically call for an *already-empty* tome always consume it either way.
Takes effect immediately.

### `DIFFICULTY_MODE` — `easy`, `normal` (default), `hard`
Shifts every glowing-material cost one step along:

```
glowstone → glowing powder → glowing ingot → glowing gem → glowing gem block
```

`easy` makes everything one tier cheaper, `hard` one tier more expensive.

*Restart the game after changing this.*

### `ENABLE_DRAGON_HEAD_UPGRADE` — default `true`
Enables the recipe upgrading a stage-4 Ice and Fire dragon skull to a custom stage 5. Set to `false`
if you find it too generous.

*Restart the game after changing this.*

---

## Missing recipes?

Almost always `PACK_MODE`. Check your log for `Could not find ...` lines — they name exactly what
each skipped recipe was looking for.

Note that `DIFFICULTY_MODE`, `PACK_MODE` and `ENABLE_DRAGON_HEAD_UPGRADE` only apply on launch;
changing them mid-game does nothing until you restart.

---

## Building from source

Put the two jars listed in [`deps/README.md`](deps/README.md) into `deps/` first — they're compiled
against but not redistributed here. Then:

```bash
./gradlew build
```

The output to install is `build/libs/RLCraftEnchantRecipes-<version>.jar` — not the `-dev` or
`-sources` jars. Don't raise `forge_version`; the comment in `gradle.properties` explains why.

## License

[MIT](LICENSE) — © 2026 ExiledRadio. Use it, fork it, put it in a modpack; just keep the copyright
notice.

## Credits

Built on [juanmuscaria's ForgeGradle 2.3 fork](https://github.com/juanmuscaria/ForgeGradle/tree/FG_2.3)
and its 1.12.2 workspace template. All referenced items, blocks and enchantments belong to their
respective mod authors — this mod bundles none of their code or assets.
