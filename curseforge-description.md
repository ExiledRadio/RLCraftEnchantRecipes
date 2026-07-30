# RLCraft Enchantment Recipes

**Stop trading with villagers. Start crafting.**

You've killed the dragon. You've got a vault full of glowing gems, dragon bone, sea serpent scales and hydra hearts. And you're still standing in front of a librarian, rerolling trades, hoping this time it offers Protection IV.

This mod fixes that. Every enchantment gets **one crafting recipe, at its maximum level** — and the ingredients are the things RLCraft actually made you work for.

![Supreme Sharpness V recipe](https://media.forgecdn.net/attachments/1831/240/supremesharpnessv-png.png)

*Supreme Sharpness V — glowing gems, two Advanced Sharpness V books, black dragon scales and a Dragon's Eye.*

---

## What you get

- **Recipes for well over a hundred enchantments**, vanilla and modded alike — Mending, Protection IV, Supreme Sharpness, Ancient Sword Mastery, the Runes, the whole Advanced and Supreme tier.
- **Always max level.** No stacking, no anvil chains, no wasted XP on a Sharpness II you'll throw away.
- **Ingredients that mean something.** Recipes are themed to the enchantment: Fire Protection wants blaze rods, Viper wants a dragon heart and a wither skull, Luck of the Sea wants ocean loot. Your rarest drops finally have a use.
- **Full JEI integration.** Every recipe is self-describing — look up any enchanted book and the grid is right there.
- **Three difficulty settings**, so you can price the whole mod up or down with one option.
- **Works on RLCraft *and* RLCraft Dregora.**

Nothing here trivialises the game. These are endgame recipes for endgame materials — the point is to turn a slot-machine grind into something you can plan and work toward.

![Protection IV recipe](https://media.forgecdn.net/attachments/1831/245/protectioniv-png.png)

*Protection IV — a full diamond armour set around a book.*

![Unbreaking III recipe](https://media.forgecdn.net/attachments/1831/241/unbreakingiii-png.png)

*Unbreaking III — glowing powder, anvils and obsidian.*

![Mending recipe](https://media.forgecdn.net/attachments/1831/244/mending-png.png)

*Mending — glowing ingots, experience bottles and a full XP Tome.*

![Strengthened Vitality V recipe](https://media.forgecdn.net/attachments/1831/246/strengthenedvitalityv-png.png)

*Strengthened Vitality V — an ender dragon head, Lifesteal and Vampirism books, and a Ring of Regeneration.*

![Subject P.E. V recipe](https://media.forgecdn.net/attachments/1831/239/subjectpev-png.png)

*Subject P.E. V — an enchanted medikit, heart containers and a broken heart trinket.*

---

## Stage 5 Dragon Skull

Upgrades a stage 4 dragon skull to a stage 5. Skull in the centre, dragon bone on the edges, glowing gems in the corners. The dragon type carries over.

![Stage 5 dragon skull upgrade recipe](https://media.forgecdn.net/attachments/1831/243/dragonskullstagev-png.png)

Set `ENABLE_DRAGON_HEAD_UPGRADE` to `false` to disable it.

---

## ⚠️ Set PACK_MODE before you play

This is the one thing you have to do. Open the config and set it to match your pack:

| Your pack | Set `PACK_MODE` to |
|---|---|
| **RLCraft Dregora** | `dregora` *(default)* |
| **RLCraft** | `rlcraft` |

**If this is wrong, most recipes silently won't appear.** The two packs ship different versions of SoManyEnchantments, and it renamed most of its enchantments between them. The mod can't reliably guess which one you're on, so you tell it once and forget about it.

Restart the game after changing it — recipes are built at launch.

---

## Configuration

Config file: `config/rlcraftenchantrecipes.cfg`, or in-game via **Mods → RLCraft Enchantment Recipes → Config**.

![In-game config screen](https://media.forgecdn.net/attachments/1831/242/config-png.png)

- **`PACK_MODE`** — `dregora` (default) or `rlcraft`. See above.
- **`DIFFICULTY_MODE`** — `easy` / `normal` / `hard`. Shifts every recipe's glowing-material cost one step along `glowstone → glowing powder → glowing ingot → glowing gem → glowing gem block`. One setting reprices the entire mod.
- **`XP_TOME_LEVEL`** — `0`–`30`, default `30`. How full an XP Tome has to be for the recipes that use one. A fuller tome is always accepted; an emptier one is rejected.
- **`CONSUME_XP_BOOK_ON_CRAFT`** — default `false`. By default a full XP Tome is **drained, not destroyed** — you get the empty tome back, like a bucket. Set `true` to consume it outright.
- **`ENABLE_DRAGON_HEAD_UPGRADE`** — default `true`. See the dragon skull section above.

---

## Playing base RLCraft?

Set `PACK_MODE=rlcraft` and a handful of ingredients swap to things base RLCraft actually has:

| Recipe asks for | You use instead |
|---|---|
| Ice and Fire copper ingot | Silver ingot |
| Ice and Fire hydra heart | Fire **or** ice dragon heart |
| Ice and Fire hydra fang | Sea serpent fang |
| Spartan Weaponry iron scythe | Iron hoe |
| Curseweave Fabric | That curse's own material (rotten flesh, bone, …) |
| Prismarine block | Prismarine shard |
| Sea lantern | Prismarine crystals |

Ocean materials are stepped down deliberately — they're harder to come by in base RLCraft.

Ten enchantments don't exist in base RLCraft and simply have no recipe there: Ascetic, Breached Plating, Combat Medic, Extinguish, and the subjects Biology, Chemistry, Geography, History, Mathematics and Physics. In exchange you get **Subject English** and **Subject Science**, which only base RLCraft has.

---

## Requirements

- **Minecraft 1.12.2**, Forge **14.23.5.2847** or newer
- **RLCraft Dregora v1.1.2b** or **RLCraft 2.9.3**
- **JEI** — technically optional, but you really want it

No hard dependencies. If something's missing the affected recipe just doesn't register — the game never crashes over it.

---

## Missing recipes?

It's almost always `PACK_MODE`. Check your log for `Could not find ...` lines; each one names exactly what the skipped recipe was looking for.

Remember that `PACK_MODE`, `DIFFICULTY_MODE` and `ENABLE_DRAGON_HEAD_UPGRADE` only take effect on launch. Changing them mid-game does nothing until you restart.

---

## Feedback

Feedback and suggestions are welcome, especially on recipe costs and anything that looks mispriced.

- **Bug reports:** the [GitHub issue tracker](https://github.com/ExiledRadio/RLCraftEnchantRecipes/issues)
- **Discord:** `exiledradio`
- Or leave a comment on this page

If a recipe isn't showing up, check `PACK_MODE` first and include your log — the `Could not find ...` lines say exactly what was missing.

---

## Source & license

Source: **https://github.com/ExiledRadio/RLCraftEnchantRecipes**

Licensed **MIT** — fork it, modify it, bundle it in your modpack. Just keep the copyright notice.

*Unofficial addon. Not affiliated with or endorsed by the RLCraft or RLCraft Dregora teams. All referenced items, blocks and enchantments belong to their respective mod authors — this mod bundles none of their code or assets.*
