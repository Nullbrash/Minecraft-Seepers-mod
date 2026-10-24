# Block hall (49 kinds of floors, thin and odd blocks) and an underwater pool, for finding point display bugs.
# Shell 49x18x31 at y 192..209; the hall is x 1..30, the pool x 32..47.
fill ~0 ~-8 ~0 ~48 ~9 ~30 minecraft:stone hollow
fill ~1 ~-7 ~1 ~47 ~0 ~29 minecraft:stone
fill ~31 ~1 ~1 ~31 ~8 ~29 minecraft:stone
fill ~31 ~1 ~14 ~31 ~2 ~15 minecraft:air

# Hall: 3x3 pads in a 7x7 grid (x 2..28, z 2..26). Rows 0-3 replace the floor, rows 4-5 lie on it,
# row 6 is one block in the middle of a pad.
fill ~2 ~0 ~2 ~4 ~0 ~4 minecraft:dirt_path
fill ~6 ~0 ~2 ~8 ~0 ~4 minecraft:farmland
fill ~10 ~0 ~2 ~12 ~0 ~4 minecraft:glass
fill ~14 ~0 ~2 ~16 ~0 ~4 minecraft:ice
fill ~18 ~0 ~2 ~20 ~0 ~4 minecraft:packed_ice
fill ~22 ~0 ~2 ~24 ~0 ~4 minecraft:blue_ice
fill ~26 ~0 ~2 ~28 ~0 ~4 minecraft:tinted_glass
fill ~2 ~0 ~6 ~4 ~0 ~8 minecraft:mud
fill ~6 ~0 ~6 ~8 ~0 ~8 minecraft:soul_sand
fill ~10 ~0 ~6 ~12 ~0 ~8 minecraft:soul_soil
fill ~14 ~0 ~6 ~16 ~0 ~8 minecraft:honey_block
fill ~18 ~0 ~6 ~20 ~0 ~8 minecraft:slime_block
fill ~22 ~0 ~6 ~24 ~0 ~8 minecraft:clay
fill ~26 ~0 ~6 ~28 ~0 ~8 minecraft:packed_mud
fill ~2 ~0 ~10 ~4 ~0 ~12 minecraft:grass_block
fill ~6 ~0 ~10 ~8 ~0 ~12 minecraft:podzol
fill ~10 ~0 ~10 ~12 ~0 ~12 minecraft:mycelium
fill ~14 ~0 ~10 ~16 ~0 ~12 minecraft:coarse_dirt
fill ~18 ~0 ~10 ~20 ~0 ~12 minecraft:rooted_dirt
fill ~22 ~0 ~10 ~24 ~0 ~12 minecraft:moss_block
fill ~26 ~0 ~10 ~28 ~0 ~12 minecraft:snow_block
fill ~2 ~0 ~14 ~4 ~0 ~16 minecraft:sand
fill ~6 ~0 ~14 ~8 ~0 ~16 minecraft:gravel
fill ~10 ~0 ~14 ~12 ~0 ~16 minecraft:oak_leaves[persistent=true]
fill ~14 ~0 ~14 ~16 ~0 ~16 minecraft:hay_block
fill ~18 ~0 ~14 ~20 ~0 ~16 minecraft:white_wool
fill ~22 ~0 ~14 ~24 ~0 ~16 minecraft:sculk
fill ~26 ~0 ~14 ~28 ~0 ~16 minecraft:dried_kelp_block
fill ~2 ~1 ~18 ~4 ~1 ~20 minecraft:stone_slab
fill ~6 ~1 ~18 ~8 ~1 ~20 minecraft:oak_stairs[facing=north]
fill ~10 ~1 ~18 ~12 ~1 ~20 minecraft:white_carpet
fill ~14 ~1 ~18 ~16 ~1 ~20 minecraft:moss_carpet
fill ~18 ~1 ~18 ~20 ~1 ~20 minecraft:snow[layers=4]
fill ~22 ~1 ~18 ~24 ~1 ~20 minecraft:oak_trapdoor[half=bottom]
fill ~26 ~1 ~18 ~28 ~1 ~20 minecraft:rail
fill ~2 ~1 ~22 ~4 ~1 ~24 minecraft:oak_pressure_plate
fill ~6 ~1 ~22 ~8 ~1 ~24 minecraft:oak_fence
fill ~10 ~1 ~22 ~12 ~1 ~24 minecraft:cobblestone_wall
fill ~14 ~1 ~22 ~16 ~1 ~24 minecraft:iron_bars
fill ~18 ~1 ~22 ~20 ~1 ~24 minecraft:glass_pane
fill ~22 ~1 ~22 ~24 ~1 ~24 minecraft:cobweb
fill ~26 ~1 ~22 ~28 ~1 ~24 minecraft:scaffolding
setblock ~3 ~1 ~27 minecraft:chest
setblock ~7 ~1 ~27 minecraft:anvil
setblock ~11 ~1 ~27 minecraft:hopper
setblock ~15 ~1 ~27 minecraft:cauldron
setblock ~19 ~1 ~27 minecraft:composter
setblock ~23 ~1 ~27 minecraft:pointed_dripstone[vertical_direction=up]
setblock ~27 ~1 ~27 minecraft:amethyst_cluster[facing=up]

# Pool: water x 33..46, z 3..27, 7 deep (bottom at y-7) with stripes of sand, gravel, mud and clay,
# a shallow shelf, a pillar, seagrass and a ladder out on the east wall.
fill ~33 ~-6 ~3 ~46 ~0 ~27 minecraft:water
fill ~33 ~-7 ~3 ~36 ~-7 ~27 minecraft:sand
fill ~37 ~-7 ~3 ~40 ~-7 ~27 minecraft:gravel
fill ~41 ~-7 ~3 ~43 ~-7 ~27 minecraft:mud
fill ~44 ~-7 ~3 ~46 ~-7 ~27 minecraft:clay
fill ~33 ~-6 ~3 ~36 ~-3 ~8 minecraft:stone
fill ~40 ~-6 ~15 ~40 ~-2 ~15 minecraft:stone
setblock ~38 ~-6 ~20 minecraft:seagrass
setblock ~39 ~-6 ~21 minecraft:seagrass
setblock ~42 ~-6 ~10 minecraft:kelp
fill ~46 ~-6 ~15 ~46 ~0 ~15 minecraft:ladder[facing=west]

tp @s ~16.5 ~1 ~29.5 180 0
