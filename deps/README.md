# Local build dependencies

`build.gradle` compiles against two mod jars from this folder via a `flatDir`
repository. They are **not** committed — they belong to their respective authors,
and nothing from them is bundled into the built mod (both are `compileOnly`).

Drop these two files in here before building:

| File | Where to get it |
|---|---|
| `Baubles-1.12-1.5.2-dev.jar` | [Baubles on CurseForge](https://www.curseforge.com/minecraft/mc-mods/baubles) — the *dev* jar, from Files → 1.12.2 |
| `jei_1.12.2-4.16.1.1012.jar` | [Just Enough Items on CurseForge](https://www.curseforge.com/minecraft/mc-mods/jei) — 1.12.2 |

The exact filenames matter; they're referenced literally in `build.gradle`. If you
use different versions, update the `dependencies` block to match.
