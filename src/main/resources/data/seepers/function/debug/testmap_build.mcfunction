# Shell and solid floor (walking surface at y+1); corridor along z 13..15, rooms north (z 1..11) and south (z 17..27).
fill ~0 ~-6 ~0 ~40 ~9 ~28 minecraft:stone hollow
fill ~1 ~-5 ~1 ~39 ~0 ~27 minecraft:stone
fill ~1 ~1 ~12 ~39 ~8 ~12 minecraft:stone
fill ~1 ~1 ~16 ~39 ~8 ~16 minecraft:stone
fill ~13 ~1 ~1 ~13 ~8 ~11 minecraft:stone
fill ~26 ~1 ~1 ~26 ~8 ~11 minecraft:stone
fill ~13 ~1 ~17 ~13 ~8 ~27 minecraft:stone
fill ~26 ~1 ~17 ~26 ~8 ~27 minecraft:stone
fill ~6 ~1 ~12 ~7 ~2 ~12 minecraft:air
fill ~19 ~1 ~12 ~20 ~2 ~12 minecraft:air
fill ~32 ~1 ~12 ~33 ~2 ~12 minecraft:air
fill ~6 ~1 ~16 ~7 ~2 ~16 minecraft:air
fill ~19 ~1 ~16 ~20 ~2 ~16 minecraft:air
fill ~32 ~1 ~16 ~33 ~2 ~16 minecraft:air
# Corridor: a lantern at the west end (light fading test).
setblock ~1 ~1 ~14 minecraft:lantern

# N1 materials: 3x3 patches of loose, soft, wet and warm floors, a shallow pool, a lantern in the corner.
fill ~5 ~0 ~2 ~7 ~0 ~4 minecraft:sand
fill ~8 ~0 ~2 ~10 ~0 ~4 minecraft:gravel
fill ~2 ~0 ~6 ~4 ~0 ~8 minecraft:dirt
fill ~5 ~0 ~6 ~7 ~0 ~8 minecraft:mud
fill ~8 ~0 ~6 ~10 ~0 ~8 minecraft:magma_block
fill ~2 ~0 ~9 ~4 ~0 ~10 minecraft:white_wool
fill ~8 ~0 ~9 ~10 ~0 ~10 minecraft:snow_block
fill ~11 ~0 ~2 ~12 ~0 ~4 minecraft:water
setblock ~1 ~1 ~1 minecraft:lantern

# N2 cliff: a 5-block drop with 1-block steps down along the west wall and a ladder back up.
fill ~14 ~-4 ~1 ~25 ~0 ~6 minecraft:air
fill ~14 ~-4 ~6 ~15 ~-1 ~6 minecraft:stone
fill ~14 ~-4 ~5 ~15 ~-2 ~5 minecraft:stone
fill ~14 ~-4 ~4 ~15 ~-3 ~4 minecraft:stone
fill ~14 ~-4 ~3 ~15 ~-4 ~3 minecraft:stone
fill ~24 ~-4 ~6 ~24 ~0 ~6 minecraft:ladder[facing=north]

# N3 parkour over a pit with water at the bottom: pillars, a slab and a fence post, a 2-gap route on the east.
fill ~27 ~-4 ~1 ~39 ~0 ~11 minecraft:air
fill ~27 ~-4 ~1 ~39 ~-4 ~11 minecraft:water
fill ~31 ~-4 ~10 ~34 ~0 ~11 minecraft:stone
fill ~33 ~-3 ~8 ~33 ~0 ~8 minecraft:stone
fill ~31 ~-3 ~6 ~31 ~0 ~6 minecraft:stone
fill ~31 ~-3 ~4 ~31 ~-1 ~4 minecraft:stone
setblock ~31 ~0 ~4 minecraft:stone_slab
fill ~33 ~-3 ~3 ~33 ~-1 ~3 minecraft:stone
setblock ~33 ~0 ~3 minecraft:oak_fence
fill ~37 ~-3 ~8 ~37 ~0 ~8 minecraft:stone
fill ~37 ~-3 ~5 ~37 ~0 ~5 minecraft:stone
fill ~31 ~-4 ~1 ~38 ~0 ~1 minecraft:stone
fill ~35 ~-3 ~10 ~35 ~0 ~10 minecraft:ladder[facing=east]

# S1 stairs and ladder up to a mezzanine.
fill ~1 ~4 ~23 ~12 ~4 ~27 minecraft:stone
setblock ~5 ~1 ~19 minecraft:stone_brick_stairs[facing=south]
setblock ~6 ~1 ~19 minecraft:stone_brick_stairs[facing=south]
setblock ~5 ~2 ~20 minecraft:stone_brick_stairs[facing=south]
setblock ~6 ~2 ~20 minecraft:stone_brick_stairs[facing=south]
setblock ~5 ~3 ~21 minecraft:stone_brick_stairs[facing=south]
setblock ~6 ~3 ~21 minecraft:stone_brick_stairs[facing=south]
setblock ~5 ~4 ~22 minecraft:stone_brick_stairs[facing=south]
setblock ~6 ~4 ~22 minecraft:stone_brick_stairs[facing=south]
fill ~1 ~1 ~22 ~1 ~5 ~22 minecraft:ladder[facing=east]

# S2 low serpentine maze: 2 blocks of headroom (jumping bumps the head).
fill ~14 ~3 ~17 ~25 ~8 ~27 minecraft:stone
fill ~14 ~1 ~19 ~23 ~2 ~19 minecraft:stone
fill ~16 ~1 ~21 ~25 ~2 ~21 minecraft:stone
fill ~14 ~1 ~23 ~23 ~2 ~23 minecraft:stone
fill ~16 ~1 ~25 ~25 ~2 ~25 minecraft:stone

# S3 water stream pushing into a brick wall, and a 1-wide slot between cobblestone walls.
fill ~30 ~1 ~21 ~36 ~3 ~21 minecraft:stone_bricks
setblock ~33 ~1 ~27 minecraft:water
fill ~28 ~1 ~23 ~28 ~2 ~27 minecraft:cobblestone
fill ~30 ~1 ~23 ~30 ~2 ~26 minecraft:cobblestone

# Annex east: block hall and pool, through a door at the corridor's east end.
execute positioned ~40 ~ ~ run function seepers:debug/testmap_annex
fill ~40 ~1 ~13 ~40 ~2 ~15 minecraft:air

# Double doors in every doorway: closed, each room keeps the fog out; an open door lets it in.
setblock ~6 ~1 ~12 minecraft:oak_door[facing=south,half=lower,hinge=left]
setblock ~6 ~2 ~12 minecraft:oak_door[facing=south,half=upper,hinge=left]
setblock ~7 ~1 ~12 minecraft:oak_door[facing=south,half=lower,hinge=right]
setblock ~7 ~2 ~12 minecraft:oak_door[facing=south,half=upper,hinge=right]
setblock ~6 ~1 ~16 minecraft:oak_door[facing=north,half=lower,hinge=left]
setblock ~6 ~2 ~16 minecraft:oak_door[facing=north,half=upper,hinge=left]
setblock ~7 ~1 ~16 minecraft:oak_door[facing=north,half=lower,hinge=right]
setblock ~7 ~2 ~16 minecraft:oak_door[facing=north,half=upper,hinge=right]
setblock ~19 ~1 ~12 minecraft:oak_door[facing=south,half=lower,hinge=left]
setblock ~19 ~2 ~12 minecraft:oak_door[facing=south,half=upper,hinge=left]
setblock ~20 ~1 ~12 minecraft:oak_door[facing=south,half=lower,hinge=right]
setblock ~20 ~2 ~12 minecraft:oak_door[facing=south,half=upper,hinge=right]
setblock ~19 ~1 ~16 minecraft:oak_door[facing=north,half=lower,hinge=left]
setblock ~19 ~2 ~16 minecraft:oak_door[facing=north,half=upper,hinge=left]
setblock ~20 ~1 ~16 minecraft:oak_door[facing=north,half=lower,hinge=right]
setblock ~20 ~2 ~16 minecraft:oak_door[facing=north,half=upper,hinge=right]
setblock ~32 ~1 ~12 minecraft:oak_door[facing=south,half=lower,hinge=left]
setblock ~32 ~2 ~12 minecraft:oak_door[facing=south,half=upper,hinge=left]
setblock ~33 ~1 ~12 minecraft:oak_door[facing=south,half=lower,hinge=right]
setblock ~33 ~2 ~12 minecraft:oak_door[facing=south,half=upper,hinge=right]
setblock ~32 ~1 ~16 minecraft:oak_door[facing=north,half=lower,hinge=left]
setblock ~32 ~2 ~16 minecraft:oak_door[facing=north,half=upper,hinge=left]
setblock ~33 ~1 ~16 minecraft:oak_door[facing=north,half=lower,hinge=right]
setblock ~33 ~2 ~16 minecraft:oak_door[facing=north,half=upper,hinge=right]
setblock ~40 ~1 ~15 minecraft:stone
setblock ~40 ~2 ~15 minecraft:stone
setblock ~40 ~1 ~13 minecraft:oak_door[facing=west,half=lower,hinge=left]
setblock ~40 ~2 ~13 minecraft:oak_door[facing=west,half=upper,hinge=left]
setblock ~40 ~1 ~14 minecraft:oak_door[facing=west,half=lower,hinge=right]
setblock ~40 ~2 ~14 minecraft:oak_door[facing=west,half=upper,hinge=right]
setblock ~71 ~1 ~14 minecraft:oak_door[facing=west,half=lower,hinge=left]
setblock ~71 ~2 ~14 minecraft:oak_door[facing=west,half=upper,hinge=left]
setblock ~71 ~1 ~15 minecraft:oak_door[facing=west,half=lower,hinge=right]
setblock ~71 ~2 ~15 minecraft:oak_door[facing=west,half=upper,hinge=right]

tp @s ~20.5 ~1 ~14.5 -90 0
