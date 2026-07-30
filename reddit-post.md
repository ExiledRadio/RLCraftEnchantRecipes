# r/rlcraft launch post

**Post as:** Image gallery post (recipe screenshots from `docs/images/`), or a text post
with the CurseForge link. Gallery posts get noticeably more engagement on this sub.

**Link to use:** https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes
(NOT the `/preview` URL — that only works while logged in as the author.)

---

## Title options

1. I got tired of rerolling librarians, so I made a mod that adds crafting recipes for max-level
   enchantment books
2. Made a mod that lets you craft max-level enchant books out of RLCraft materials instead of
   grinding villagers
3. [Mod] RLCraft Enchantment Recipes — every enchantment, max level, as a crafting recipe

*Recommended: #1. It leads with the shared frustration rather than the product.*

---

## Body

I've been playing Dregora and got sick of the librarian reroll loop, so I spent the last few
weeks building a mod that turns endgame enchantments into crafting recipes instead.

Every enchantment gets one recipe, always at max level. The ingredients are themed to the
enchantment and lean on materials you've probably got sitting in a chest doing nothing —
Fire Protection wants blaze rods, Viper wants a dragon heart and a wither skull, Supreme
Sharpness wants black dragon scales and a Dragon's Eye. Over a hundred enchantments covered,
vanilla and modded, including the Advanced/Supreme tiers and the Runes.

It's not meant to be a shortcut. The recipes are priced as endgame projects — the point is that
you can actually work toward a specific enchantment instead of hoping RNG hands it to you.

A few details:

- Full JEI support, so you can just look up any enchanted book and see the grid
- Works on **both base RLCraft and Dregora** — there's a `PACK_MODE` config option you set to
  match your pack. This matters: the two packs ship different versions of SoManyEnchantments
  and it renamed most of its enchantments between them, so if `PACK_MODE` is wrong most recipes
  won't show up
- `DIFFICULTY_MODE` (easy/normal/hard) shifts every recipe's cost up or down a tier if you think
  I've priced things wrong
- Also throws in a stage 4 → stage 5 dragon skull upgrade recipe

CurseForge: <LINK>
Source (MIT): https://github.com/ExiledRadio/RLCraftEnchantRecipes

Genuinely open to feedback on the recipe costs — I balanced them against my own playthrough,
which is a sample size of one. If something looks way off, tell me and I'll adjust it.

---

## Before posting — checklist

- [ ] Mod approved and publicly visible on CurseForge (check the non-preview URL in a private
      window; if it 404s it isn't live yet)
- [ ] Read r/rlcraft's rules on self-promotion / mod release posts
- [ ] Apply the right flair (most likely "Mod" or "Discussion" — check what similar posts use)
- [ ] Attach 4–6 recipe screenshots from `docs/images/` as the gallery
- [ ] Be around for the first few hours to answer comments — early replies drive visibility

## First comment to pin (optional)

Common question pre-empted:

> To be clear this is unofficial and not affiliated with the RLCraft or Dregora teams — just a
> player-made addon. And if recipes aren't showing up for you, check `PACK_MODE` in the config
> first, that's almost always the cause.
