/*
 * This file is part of WorldPainter Languages, an unofficial localization
 * fork of WorldPainter (https://github.com/saplome/WorldPainter-LANGUAGES).
 *
 * Original work Copyright © pepsoft.org, The Netherlands.
 * Modifications Copyright © 2026 saplome. This file was modified in 2026.
 *
 * This file remains licensed under the GNU General Public License,
 * version 3. See the LICENSE file for details.
 */

package org.pepsoft.worldpainter.layers.exporters;

import com.google.common.collect.ImmutableSet;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.mdc.MDCWrappingRuntimeException;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.NoiseSettings;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.exporting.AbstractLayerExporter;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.heightMaps.NoiseHeightMap;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.exporters.AbstractCavesExporter.CaveDecorationSettings.Decoration;
import org.pepsoft.worldpainter.util.BiomeUtils;

import java.util.*;

import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.*;
import static org.pepsoft.worldpainter.Constants.V_1_17;
import static org.pepsoft.worldpainter.DefaultPlugin.ATTRIBUTE_MC_VERSION;
import static org.pepsoft.worldpainter.Platform.Capability.BIOMES_3D;
import static org.pepsoft.worldpainter.Platform.Capability.NAMED_BIOMES;
import static org.pepsoft.worldpainter.biomeschemes.Minecraft1_17Biomes.BIOME_DRIPSTONE_CAVES;
import static org.pepsoft.worldpainter.biomeschemes.Minecraft1_17Biomes.BIOME_LUSH_CAVES;
import static org.pepsoft.worldpainter.layers.exporters.AbstractCavesExporter.CaveDecorationSettings.Decoration.DRIPSTONE_CAVE_PATCHES;
import static org.pepsoft.worldpainter.layers.exporters.AbstractCavesExporter.CaveDecorationSettings.Decoration.LUSH_CAVE_PATCHES;
import static org.pepsoft.worldpainter.layers.exporters.WPObjectExporter.renderObject;
import static org.pepsoft.worldpainter.layers.exporters.WPObjectExporter.renderObjectInverted;
import static org.pepsoft.worldpainter.layers.plants.Plants.GRASS;
import static org.pepsoft.worldpainter.layers.plants.Plants.MOSS_CARPET;
import static org.pepsoft.worldpainter.layers.plants.Plants.SPORE_BLOSSOM;
import static org.pepsoft.worldpainter.layers.plants.Plants.*;

/**
 * Abstract base class for cave carving exporters, providing common
 * functionality for excavating caves. Intended use:
 *
 * <ul><li>For each column (x and y coordinate):
 *     <li>Invoke {@link #setupForColumn(long, Tile, int, int, boolean, boolean, boolean, boolean)} once
 *     <li>Going from the top to the bottom of the cave for that column, invoke {@link #processBlock(Chunk, int, int, int, boolean)}
 * </ul>
 *
 * @param <L> The cave layer type.
 */
public abstract class AbstractCavesExporter<L extends Layer> extends AbstractLayerExporter<L> {
    public AbstractCavesExporter(Dimension dimension, Platform platform, CaveSettings settings, L layer) {
        super(dimension, platform, settings, layer);
        decorationSettings = (settings != null) ? settings.getCaveDecorationSettings() : null;
        if (decorationSettings != null) {
            decorateBrownMushrooms = decorationSettings.isEnabled(Decoration.BROWN_MUSHROOM);
            final boolean mcVersionAtLeast1_17 = platform.getAttribute(ATTRIBUTE_MC_VERSION).isAtLeast(V_1_17);
            decorateGlowLichen = decorationSettings.isEnabled(Decoration.GLOW_LICHEN) && mcVersionAtLeast1_17;
            decorateLushCaves = decorationSettings.isEnabled(Decoration.LUSH_CAVE_PATCHES) && mcVersionAtLeast1_17;
            decorateDripstoneCaves = decorationSettings.isEnabled(Decoration.DRIPSTONE_CAVE_PATCHES) && mcVersionAtLeast1_17;
            decorationEnabled = decorateBrownMushrooms || decorateGlowLichen || decorateLushCaves || decorateDripstoneCaves;
            lushCaveNoise = decorateLushCaves ? new NoiseHeightMap(decorationSettings.noiseSettingsMap.get(LUSH_CAVE_PATCHES), dimension.getSeed() + 1) : null;
            dripstoneCaveNoise = decorateDripstoneCaves ? new NoiseHeightMap(decorationSettings.noiseSettingsMap.get(DRIPSTONE_CAVE_PATCHES), dimension.getSeed() + 2) : null;
            lushPoolNoise = decorateLushCaves ? new NoiseHeightMap(1.0, 0.20, 2, dimension.getSeed() + 31) : null;
            dripstoneFeatureNoise = decorateDripstoneCaves ? new NoiseHeightMap(1.0, 0.24, 2, dimension.getSeed() + 37) : null;
            dripstonePatchNoise = decorateDripstoneCaves ? new NoiseHeightMap(1.0, 1.8, 2, dimension.getSeed() + 41) : null;
            biomeUtils = new BiomeUtils(dimension);
            setBiomes = platform.capabilities.contains(BIOMES_3D) || platform.capabilities.contains(NAMED_BIOMES);
        } else {
            decorationEnabled = decorateBrownMushrooms = decorateGlowLichen = decorateLushCaves = decorateDripstoneCaves = setBiomes = false;
            lushCaveNoise = dripstoneCaveNoise = lushPoolNoise = dripstoneFeatureNoise = dripstonePatchNoise = null;
            biomeUtils = null;
        }
    }

    protected final void setupForColumn(long seed, Tile tile, int maxY, int waterLevel, boolean glassCeiling, boolean surfaceBreaking, boolean leaveWater, boolean floodWithLava) {
        State state = new State();
        state.seed = seed;
        state.tile = tile;
        state.maxY = maxY;
        state.waterLevel = waterLevel;
        state.glassCeiling = glassCeiling;
        state.surfaceBreaking = surfaceBreaking;
        state.leaveWater = leaveWater;
        state.floodWithLava = floodWithLava;
        STATE_HOLDER.set(state);
    }

    protected final void resetColumn() {
        final State state = STATE_HOLDER.get();
        state.breachedCeiling = false;
        state.previousBlockInCavern = false;
    }

    protected final void emptyBlockEncountered() {
        State state = STATE_HOLDER.get();
        state.breachedCeiling = true;
        state.previousBlockInCavern = true;
    }

    protected final void processBlock(Chunk chunk, int x, int y, int z, boolean excavate) {
        processBlock(chunk, x, y, z, excavate, true);
    }

    /**
     * Processes a cave block while optionally suppressing aquifer fluid for this particular cave family.
     * Existing exporters use the five-argument overload and retain their original behaviour.
     */
    protected final void processBlock(Chunk chunk, int x, int y, int z, boolean excavate, boolean allowFlooding) {
        final State state = STATE_HOLDER.get();
        if (excavate) {
            // In a cavern
            if ((! state.breachedCeiling) && (y < state.maxY)) {
                if (state.glassCeiling) {
                    final int terrainheight = state.tile.getIntHeight(x, z);
                    for (int yy = y + 1; yy <= terrainheight; yy++) {
                        chunk.setMaterial(x, yy, z, GLASS);
                    }
                }
                if (state.surfaceBreaking) {
                    final Material blockAbove = chunk.getMaterial(x, y + 1, z);
                    if (! state.leaveWater) {
                        final int terrainheight = state.tile.getIntHeight(x, z);
                        if (blockAbove.isNamed(MC_WATER)) {
                            for (int yy = y + 1; yy <= terrainheight; yy++) {
                                if (chunk.getMaterial(x, yy, z).isNamed(MC_WATER)) {
                                    chunk.setMaterial(x, yy, z, AIR);
                                } else {
                                    break;
                                }
                            }
                        } else if (blockAbove.isNamed(MC_LAVA)) {
                            for (int yy = y + 1; yy <= terrainheight; yy++) {
                                if (chunk.getMaterial(x, yy, z).isNamed(MC_LAVA)) {
                                    chunk.setMaterial(x, yy, z, AIR);
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            state.breachedCeiling = true;
            if ((y > state.waterLevel) || (! allowFlooding)) {
                chunk.setMaterial(x, y, z, AIR);
                state.previousBlockInCavern = true;
            } else {
                if (state.floodWithLava) {
                    chunk.setMaterial(x, y, z, LAVA);
                } else {
                    chunk.setMaterial(x, y, z, WATER);
                }
                state.previousBlockInCavern = false;
            }
        } else if (state.previousBlockInCavern
                && (y >= state.waterLevel)
                && (! chunk.getMaterial(x, y, z).veryInsubstantial)) {
            state.previousBlockInCavern = false;
        }
    }

    protected final void decorateBlock(MinecraftWorld world, Random rng, int x, int y, int height) {
        decorateBlock(world, rng, x, y, height, 0);
    }

    protected final void decorateBlock(MinecraftWorld world, Random rng, int x, int y, int height, int forcedBiome) {
        if ((! decorationEnabled) || (height < minZ) || (height > maxZ)) {
            return;
        }
        final Material material = world.getMaterialAt(x, y, height);
        final boolean forceLush = (forcedBiome == 1) && decorateLushCaves
                && decorationSettings.isEnabledAt(LUSH_CAVE_PATCHES, height);
        final boolean forceDripstoneBiome = (forcedBiome == 2) && decorateDripstoneCaves
                && decorationSettings.isEnabledAt(Decoration.DRIPSTONE_CAVE_PATCHES, height);
        final boolean forceDripstonePatch = forceDripstoneBiome
                && (dripstonePatchNoise.getValue(x, y, height * 1.15)
                >= 1.0 - decorationSettings.getDripstonePatchCoverage() / 100.0);
        final float lushValue = (decorateLushCaves && (!forceLush) && (!forceDripstoneBiome))
                ? (float) lushCaveNoise.getValue(x, y, height * 2.0) : Float.NEGATIVE_INFINITY;
        final float dripstoneValue = (decorateDripstoneCaves && (!forceLush) && (!forceDripstoneBiome))
                ? (float) dripstoneCaveNoise.getValue(x, y, height * 2.0) : Float.NEGATIVE_INFINITY;
        final float lushThreshold = LUSH_CAVE_THRESHOLD + decorationSettings.getLushThresholdOffset();
        final float dripstoneThreshold = DRIPSTONE_CAVE_THRESHOLD + decorationSettings.getDripstoneThresholdOffset();
        boolean inLushCave = forceLush || (decorateLushCaves && decorationSettings.isEnabledAt(LUSH_CAVE_PATCHES, height) && (lushValue >= lushThreshold));
        boolean inDripstoneCave = forceDripstonePatch || (decorateDripstoneCaves && decorationSettings.isEnabledAt(Decoration.DRIPSTONE_CAVE_PATCHES, height) && (dripstoneValue >= dripstoneThreshold));
        if (inLushCave && inDripstoneCave && (rng.nextInt(100) >= decorationSettings.getMixedPatchChance())) {
            final float lushExcess = lushValue - lushThreshold, dripstoneExcess = dripstoneValue - dripstoneThreshold;
            if (lushExcess >= dripstoneExcess) inDripstoneCave = false; else inLushCave = false;
        }
        final boolean tagDripstoneBiome = forceDripstoneBiome || inDripstoneCave;
        if (setBiomes && (inLushCave || tagDripstoneBiome)) {
            final int terrainHeight = dimension.getIntHeightAt(x, y);
            final int biomeUpperLimit = (((terrainHeight - dimension.getTopLayerDepth(x, y, terrainHeight)) >> 2) << 2) - 1;
            if (height <= biomeUpperLimit) {
                biomeUtils.set3DBiome(world.getChunkForEditing(x >> 4, y >> 4), (x & 0xf) >> 2, height >> 2, (y & 0xf) >> 2, inLushCave ? BIOME_LUSH_CAVES : BIOME_DRIPSTONE_CAVES);
            }
        }
        if (material.empty || material.isNamed(MC_WATER)) {
            final Material materialBelow = (height > minHeight) ? world.getMaterialAt(x, y, height - 1) : null;
            if ((materialBelow != null) && (! materialBelow.veryInsubstantial) && (! materialBelow.isNamed(MC_POINTED_DRIPSTONE))) {
                int waterDepth = material.empty ? 1 : 0, spaceAvailable = 1;
                for (int dz = 1; (dz < 7) && (height + dz < maxHeight); dz++) {
                    if (world.getMaterialAt(x, y, height + dz).isNamed(MC_WATER)) {
                        waterDepth++;
                    } else if ((! world.getMaterialAt(x, y, height + dz).empty)) {
                        break;
                    }
                    spaceAvailable++;
                }
                if (decorateFloor(world, rng, x, y, height, inLushCave, inDripstoneCave, material, materialBelow, spaceAvailable, waterDepth)) {
                    return;
                }
            }
            final Material materialAbove = (height < (maxHeight - 1)) ? world.getMaterialAt(x, y, height + 1) : null;
            if ((materialAbove != null) && (! materialAbove.veryInsubstantial) && (! materialAbove.isNamed(MC_POINTED_DRIPSTONE))) {
                int spaceAvailable = 1, drySpaceAvailable = material.empty ? 1 : 0;
                for (int dz = 1; (dz < 7) && (height - dz >= minHeight); dz++) {
                    final Material material1 = world.getMaterialAt(x, y, height - dz);
                    if (((! material1.empty)) && material1.isNotNamed(MC_WATER)) {
                        break;
                    } else if (material1.empty && (drySpaceAvailable != 0)){
                        drySpaceAvailable++;
                    }
                    spaceAvailable++;
                }
                if (decorateRoof(world, rng, x, y, height, inLushCave, inDripstoneCave, material, materialAbove, spaceAvailable, drySpaceAvailable)) {
                    return;
                }
            }
            final Material materialNorth = world.getMaterialAt(x, y - 1, height);
            final Material materialSouth = world.getMaterialAt(x, y + 1, height);
            final Material materialEast = world.getMaterialAt(x + 1, y, height);
            final Material materialWest = world.getMaterialAt(x - 1, y, height);
            if (((! materialNorth.veryInsubstantial) && (! materialNorth.isNamed(MC_POINTED_DRIPSTONE)))
                    || ((! materialSouth.veryInsubstantial) && (! materialSouth.isNamed(MC_POINTED_DRIPSTONE)))
                    || ((! materialEast.veryInsubstantial) && (! materialEast.isNamed(MC_POINTED_DRIPSTONE)))
                    || ((! materialWest.veryInsubstantial) && (! materialWest.isNamed(MC_POINTED_DRIPSTONE)))) {
                decorateWall(world, rng, x, y, height, inLushCave, material, materialNorth, materialSouth, materialEast, materialWest);
            }
        }
    }

    private boolean decorateFloor(MinecraftWorld world, Random rng, int x, int y, int height, boolean inLushCave, boolean inDripstoneCave, Material existingMaterial, Material materialBelow, int spaceAvailable, int waterDepth) {
        if (decorateBrownMushrooms && decorationSettings.isEnabledAt(Decoration.BROWN_MUSHROOM, height) && (rng.nextInt(MUSHROOM_CHANCE) == 0) && existingMaterial.isNotNamed(MC_WATER)) {
            world.setMaterialAt(x, y, height, BROWN_MUSHROOM);
            return true;
        } else if (decorateGlowLichen && decorationSettings.isEnabledAt(Decoration.GLOW_LICHEN, height) && rng.nextInt(MUSHROOM_CHANCE) == 0) {
            if (existingMaterial.isNamed(MC_WATER)) {
                world.setMaterialAt(x, y, height, GLOW_LICHEN_DOWN.withProperty(WATERLOGGED, true));
            } else {
                world.setMaterialAt(x, y, height, GLOW_LICHEN_DOWN);
            }
            return true;
        } else if (inDripstoneCave && (waterDepth <= 2)) {
            if (decorationSettings.isEnhancedDripstoneFeatures()
                    && maybeRenderLargeDripstone(world, x, y, height, existingMaterial)) {
                return true;
            }
            if (rng.nextInt(100) < decorationSettings.getSmallDripstoneFrequency()) {
                // Check whether we are actually below something that could generate a stalactite
                for (int z = height + 1; z <= dimension.getIntHeightAt(x, y); z++) {
                    final Material material = world.getMaterialAt(x, y, z);
                    if (SUPPORTS_DRIPSTONE.contains(material.name) || material.isNamed(MC_POINTED_DRIPSTONE)) {
                        renderStalagmite(world, x, y, height, rng.nextInt(Math.min(5, spaceAvailable)) + 1, rng);
                        return true;
                    } else if (material.solid) {
                        break;
                    }
                }
            }
        }
        if (inLushCave) {
            final float poolPatch = (decorationSettings.isEnhancedLushFeatures() && existingMaterial.empty
                    && (spaceAvailable >= 2) && ((height - 1) >= minZ))
                    ? getLushPoolPatch(world, x, y, height) : 0.0f;
            if (poolPatch != 0.0f) {
                final float poolStrength = Math.abs(poolPatch);
                world.setMaterialAt(x, y, height - 1, CLAY);
                if ((poolStrength > 0.48f) && ((height - 2) >= minZ)) {
                    final Material deepFloor = world.getMaterialAt(x, y, height - 2);
                    if (deepFloor.solid && deepFloor.natural) world.setMaterialAt(x, y, height - 2, CLAY);
                }
                if (poolPatch > 0.0f) {
                    world.setMaterialAt(x, y, height, WATER);
                    clayPoolBank(world, x, y - 1, height);
                    clayPoolBank(world, x, y + 1, height);
                    clayPoolBank(world, x - 1, y, height);
                    clayPoolBank(world, x + 1, y, height);
                    final int plantRoll = rng.nextInt(16);
                    if ((plantRoll < 4) && (spaceAvailable > 1)) {
                        renderObject(world, dimension, platform, SMALL_DRIPLEAF.realise(2, platform), x, y, height);
                    } else if ((plantRoll == 4) && (spaceAvailable > 3)) {
                        renderObject(world, dimension, platform, BIG_DRIPLEAF.realise(rng.nextInt(Math.min(4, spaceAvailable - 2)) + 3, platform), x, y, height);
                    }
                }
                return true;
            }
            if ((height - 1) >= minZ) {
                world.setMaterialAt(x, y, height - 1, (decorationSettings.isEnhancedLushFeatures() && existingMaterial.isNamed(MC_WATER)) ? CLAY : MOSS_BLOCK);
            }
            if (existingMaterial.isNamed(MC_WATER)) {
                switch (rng.nextInt(10)) {
                    case 0:
                    case 1:
                        if (spaceAvailable > 1) {
                            renderObject(world, dimension, platform, SMALL_DRIPLEAF.realise(2, platform), x, y, height);
                        }
                        break;
                    case 2:
                    case 3:
                        if (spaceAvailable > 2) {
                            renderObject(world, dimension, platform, BIG_DRIPLEAF.realise(rng.nextInt(Math.min(5, spaceAvailable - 2)) + 3, platform), x, y, height);
                        }
                        break;
                }
            } else {
                switch (rng.nextInt(20)) {
                    case 0:
                    case 1:
                    case 2:
                        renderObject(world, dimension, platform, GRASS.realise(1, platform), x, y, height);
                        break;
                    case 3:
                    case 4:
                    case 5:
                        if (spaceAvailable > 1) {
                            renderObject(world, dimension, platform, TALL_GRASS.realise(2, platform), x, y, height);
                        }
                        break;
                    case 6:
                        renderObject(world, dimension, platform, SAPLING_FLOWERING_AZALEA.realise(1, platform), x, y, height);
                        break;
                    case 7:
                    case 8:
                    case 9:
                        renderObject(world, dimension, platform, MOSS_CARPET.realise(1, platform), x, y, height);
                        break;
                }
            }
        }
        return false;
    }

    private boolean decorateRoof(MinecraftWorld world, Random rng, int x, int y, int height, boolean inLushCave, boolean inDripstoneCave, Material existingMaterial, Material materialAbove, int spaceAvailable, int drySpaceAvailable) {
        if (decorateGlowLichen && decorationSettings.isEnabledAt(Decoration.GLOW_LICHEN, height) && rng.nextInt(MUSHROOM_CHANCE) == 0) {
            if (existingMaterial.isNamed(MC_WATER)) {
                world.setMaterialAt(x, y, height, GLOW_LICHEN_UP.withProperty(WATERLOGGED, true));
            } else {
                world.setMaterialAt(x, y, height, GLOW_LICHEN_UP);
            }
            return true;
        } else if (inDripstoneCave && SUPPORTS_DRIPSTONE.contains(materialAbove.name)) {
            if (rng.nextInt(100) < decorationSettings.getSmallDripstoneFrequency()) {
                renderStalactite(world, x, y, height, rng.nextInt(Math.min(5, spaceAvailable)) + 1, rng);
            }
        }
        if (inLushCave) {
            if ((height + 1) <= maxZ) {
                world.setMaterialAt(x, y, height + 1, MOSS_BLOCK);
            }
            if (! existingMaterial.isNamed(MC_WATER)) {
                switch (rng.nextInt(50)) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        renderObjectInverted(world, platform, GLOW_BERRIES.realise(rng.nextInt(Math.min(5, drySpaceAvailable)) + 1, platform), x, y, height);
                        break;
                    case 5:
                        renderObject(world, dimension, platform, SPORE_BLOSSOM.realise(1, platform), x, y, height);
                        break;
                    case 6:
                        if (decorationSettings.isEnhancedLushFeatures() && ((height + 1) <= maxZ)) {
                            world.setMaterialAt(x, y, height + 1, ROOTED_DIRT);
                            world.setMaterialAt(x, y, height, Material.HANGING_ROOTS);
                        }
                        break;
                }
            }
        }
        return false;
    }

    private void decorateWall(MinecraftWorld world, Random rng, int x, int y, int height, boolean inLushCave, Material existingMaterial, Material materialNorth, Material materialSouth, Material materialEast, Material materialWest) {
        if (decorateGlowLichen && decorationSettings.isEnabledAt(Decoration.GLOW_LICHEN, height) && rng.nextInt(MUSHROOM_CHANCE) == 0) {
            Material material = GLOW_LICHEN_NONE;
            if (existingMaterial.isNamed(MC_WATER)) {
                material = GLOW_LICHEN_NONE.withProperty(WATERLOGGED, true);
            }
            final List<Direction> directions = new ArrayList<>(4);
            if ((! materialNorth.veryInsubstantial) && (! materialNorth.isNamed(MC_POINTED_DRIPSTONE))) {
                directions.add(Direction.NORTH);
            }
            if ((! materialSouth.veryInsubstantial) && (! materialSouth.isNamed(MC_POINTED_DRIPSTONE))) {
                directions.add(Direction.SOUTH);
            }
            if ((! materialEast.veryInsubstantial) && (! materialEast.isNamed(MC_POINTED_DRIPSTONE))) {
                directions.add(Direction.EAST);
            }
            if ((! materialWest.veryInsubstantial) && (! materialWest.isNamed(MC_POINTED_DRIPSTONE))) {
                directions.add(Direction.WEST);
            }
            world.setMaterialAt(x, y, height, material.withProperty(directions.get(rng.nextInt(directions.size())).name().toLowerCase(), "true"));
        }
        if (inLushCave) {
            if ((! materialNorth.veryInsubstantial) && (! materialNorth.isNamed(MC_POINTED_DRIPSTONE))) {
                world.setMaterialAt(x, y - 1, height, (decorationSettings.isEnhancedLushFeatures() && existingMaterial.isNamed(MC_WATER)) ? CLAY : MOSS_BLOCK);
            }
            if ((! materialSouth.veryInsubstantial) && (! materialSouth.isNamed(MC_POINTED_DRIPSTONE))) {
                world.setMaterialAt(x, y + 1, height, (decorationSettings.isEnhancedLushFeatures() && existingMaterial.isNamed(MC_WATER)) ? CLAY : MOSS_BLOCK);
            }
            if ((! materialEast.veryInsubstantial) && (! materialEast.isNamed(MC_POINTED_DRIPSTONE))) {
                world.setMaterialAt(x + 1, y, height, (decorationSettings.isEnhancedLushFeatures() && existingMaterial.isNamed(MC_WATER)) ? CLAY : MOSS_BLOCK);
            }
            if ((! materialWest.veryInsubstantial) && (! materialWest.isNamed(MC_POINTED_DRIPSTONE))) {
                world.setMaterialAt(x - 1, y, height, (decorationSettings.isEnhancedLushFeatures() && existingMaterial.isNamed(MC_WATER)) ? CLAY : MOSS_BLOCK);
            }
        }
    }
    private float getLushPoolPatch(MinecraftWorld world, int x, int y, int height) {
        final int poolCellSize = decorationSettings.getLushPoolCellSize();
        final int cellX = Math.floorDiv(x, poolCellSize), cellY = Math.floorDiv(y, poolCellSize);
        float result = 0.0f;
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcy = -1; dcy <= 1; dcy++) {
                final int candidateX = cellX + dcx, candidateY = cellY + dcy;
                final long hash = mixDecorationSeed(dimension.getSeed()
                        ^ ((long) candidateX * 0x9E3779B97F4A7C15L)
                        ^ ((long) candidateY * 0xC2B2AE3D27D4EB4FL) ^ 0x6C8E9CF570932BD5L);
                if ((hash & 0xffL) >= hashThreshold(decorationSettings.getLushPoolFrequency(), 63, 160)) continue;
                final int margin = Math.max(2, decorationSettings.getLushPoolMaxRadius() / 2);
                final int centreRange = Math.max(1, poolCellSize - margin * 2);
                final int centreX = candidateX * poolCellSize + margin + (int) Math.floorMod(hash >>> 8, centreRange);
                final int centreY = candidateY * poolCellSize + margin + (int) Math.floorMod(hash >>> 16, centreRange);
                final float radiusRange = decorationSettings.getLushPoolMaxRadius() - decorationSettings.getLushPoolMinRadius();
                final float radiusX = decorationSettings.getLushPoolMinRadius() + (float) ((hash >>> 24) & 0xffL) / 255.0f * radiusRange;
                final float radiusY = decorationSettings.getLushPoolMinRadius() + (float) ((hash >>> 32) & 0xffL) / 255.0f * radiusRange;
                final float nx = (x - centreX) / radiusX, ny = (y - centreY) / radiusY;
                final float radial = nx * nx + ny * ny;
                if (radial > 1.28f) continue;
                final int poolLevel = findCaveFloorNear(world, centreX, centreY, height, 5);
                if (poolLevel != height) continue;
                final float edge = (float) ((lushPoolNoise.getValue(x, y, height * 0.35) - 0.5) * 0.42);
                final float strength = 1.0f + edge - radial;
                if (strength > 0.0f) {
                    final boolean dry = Math.floorMod(hash >>> 40, 100L) < decorationSettings.getLushPoolDryChance();
                    final float encoded = 0.001f + strength;
                    if ((! dry) || (result == 0.0f)) result = dry ? -encoded : encoded;
                }
            }
        }
        return result;
    }

    private int findCaveFloorNear(MinecraftWorld world, int x, int y, int aroundHeight, int range) {
        for (int delta = 0; delta <= range; delta++) {
            final int lower = aroundHeight - delta;
            if ((lower > minHeight) && (lower < maxHeight)) {
                final Material at = world.getMaterialAt(x, y, lower), below = world.getMaterialAt(x, y, lower - 1);
                if ((at.empty || at.isNamed(MC_WATER)) && (! below.veryInsubstantial)) return lower;
            }
            if (delta != 0) {
                final int upper = aroundHeight + delta;
                if ((upper > minHeight) && (upper < maxHeight)) {
                    final Material at = world.getMaterialAt(x, y, upper), below = world.getMaterialAt(x, y, upper - 1);
                    if ((at.empty || at.isNamed(MC_WATER)) && (! below.veryInsubstantial)) return upper;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private void clayPoolBank(MinecraftWorld world, int x, int y, int height) {
        final Material material = world.getMaterialAt(x, y, height);
        if (material.solid && material.natural) world.setMaterialAt(x, y, height, CLAY);
    }

    private boolean maybeRenderLargeDripstone(MinecraftWorld world, int x, int y, int floor, Material existingMaterial) {
        final int largeCellSize = decorationSettings.getLargeDripstoneCellSize();
        final int cellX = Math.floorDiv(x, largeCellSize), cellY = Math.floorDiv(y, largeCellSize);
        final long hash = mixDecorationSeed(dimension.getSeed()
                ^ ((long) cellX * 0xD6E8FEB86659FD93L)
                ^ ((long) cellY * 0xA24BAED4963EE407L) ^ 0x3C79AC492BA7B653L);
        if ((hash & 0xffL) >= hashThreshold(decorationSettings.getLargeDripstoneFrequency(), 56, 144)) return false;
        final int centreX = (largeCellSize == 32)
                ? cellX * 32 + 5 + (int) ((hash >>> 8) & 0x15L)
                : cellX * largeCellSize + (int) Math.floorMod(hash >>> 8, largeCellSize);
        final int centreY = (largeCellSize == 32)
                ? cellY * 32 + 5 + (int) ((hash >>> 13) & 0x15L)
                : cellY * largeCellSize + (int) Math.floorMod(hash >>> 24, largeCellSize);
        if ((x != centreX) || (y != centreY)) return false;
        int ceiling = Integer.MIN_VALUE;
        for (int z = floor + 1; (z <= floor + decorationSettings.getLargeDripstoneSearchHeight()) && (z < maxHeight); z++) {
            final Material material = world.getMaterialAt(x, y, z);
            if ((! material.empty) && material.isNotNamed(MC_WATER)) { ceiling = z; break; }
        }
        final int caveHeight = ceiling - floor;
        if ((ceiling == Integer.MIN_VALUE) || (caveHeight < 12)) return false;
        final int maxRadius = Math.max(4, Math.min(decorationSettings.getLargeDripstoneMaxRadius(), caveHeight / 4));
        final int radius = 3 + (int) ((hash >>> 20) & 0x1fL) % (maxRadius - 2);
        final boolean stalagnate = ((hash >>> 27) & 0x07L) == 0L;
        int floorLength, ceilingLength;
        if (stalagnate) {
            floorLength = Math.max(5, Math.round(caveHeight * (0.48f + ((hash >>> 30) & 0x0fL) / 100.0f)));
            ceilingLength = caveHeight - floorLength + 1;
        } else {
            floorLength = Math.max(4, Math.round(caveHeight * (0.25f + ((hash >>> 30) & 0x0fL) / 80.0f)));
            ceilingLength = Math.max(4, Math.round(caveHeight * (0.22f + ((hash >>> 36) & 0x0fL) / 85.0f)));
            final int maxCombined = Math.max(8, caveHeight - 3);
            if ((floorLength + ceilingLength) > maxCombined) ceilingLength = Math.max(4, maxCombined - floorLength);
        }
        renderLargeDripstoneCone(world, x, y, floor - 1, floorLength + 1, radius, true);
        renderLargeDripstoneCone(world, x, y, ceiling, ceilingLength + 1, radius, false);
        return true;
    }

    private void renderLargeDripstoneCone(MinecraftWorld world, int x, int y, int baseZ, int length, int radius, boolean upward) {
        for (int step = 0; step < length; step++) {
            final float progress = step / (float) Math.max(1, length - 1);
            final float localRadius = Math.max(0.65f, radius * (float) Math.pow(1.0f - progress, 0.72f));
            final int blockRadius = Math.max(1, (int) Math.ceil(localRadius));
            final int z = baseZ + (upward ? step : -step);
            if ((z < minZ) || (z > maxZ)) continue;
            for (int dx = -blockRadius; dx <= blockRadius; dx++) {
                for (int dy = -blockRadius; dy <= blockRadius; dy++) {
                    final float irregularity = (float) ((dripstoneFeatureNoise.getValue(x + dx, y + dy, z * 0.25) - 0.5) * 0.32);
                    if ((dx * dx + dy * dy) <= localRadius * localRadius * (1.0f + irregularity)) {
                        final Material material = world.getMaterialAt(x + dx, y + dy, z);
                        if (material.empty || material.isNamed(MC_WATER) || (step == 0 && material.solid && material.natural)) {
                            world.setMaterialAt(x + dx, y + dy, z, DRIPSTONE_BLOCK);
                        }
                    }
                }
            }
        }
        final int tipLength = Math.max(1, Math.min(4, length / 7));
        for (int step = 0; step < tipLength; step++) {
            final int z = baseZ + (upward ? length + step : -length - step);
            if ((z < minZ) || (z > maxZ)) break;
            final Material material = world.getMaterialAt(x, y, z);
            if ((!material.empty) && material.isNotNamed(MC_WATER)) break;
            final Material pointed;
            if (upward) {
                pointed = (step == tipLength - 1) ? POINTED_DRIPSTONE_UP_TIP
                        : ((step == 0) ? POINTED_DRIPSTONE_UP_BASE : POINTED_DRIPSTONE_UP_FRUSTUM);
            } else {
                pointed = (step == tipLength - 1) ? POINTED_DRIPSTONE_DOWN_TIP
                        : ((step == 0) ? POINTED_DRIPSTONE_DOWN_BASE : POINTED_DRIPSTONE_DOWN_FRUSTUM);
            }
            setWaterloggedBlock(world, x, y, z, pointed);
        }
    }

    private static long mixDecorationSeed(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private void renderStalagmite(MinecraftWorld world, int x, int y, int height, int length, Random rng) {
        if ((height - 1) >= minZ) {
            world.setMaterialAt(x, y, height - 1, DRIPSTONE_BLOCK);
        }
        for (int dz = -2; dz <= 0; dz++) {
            if ((height + dz) < minZ) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (((dx != 0) || (dy != 0) || (dz != -1)) && (rng.nextInt(3) > 0)) {
                        final Material material = world.getMaterialAt(x + dx, y + dy, height + dz);
                        if (material.opaque && material.solid && material.natural && (material != DRIPSTONE_BLOCK)) {
                            world.setMaterialAt(x + dx, y + dy, height + dz, DRIPSTONE_BLOCK);
                        }
                    }
                }
            }
        }
        for (int dz = 0; dz < Math.max(length, 3); dz++) {
            final Material material = world.getMaterialAt(x, y, height + dz);
            if ((! material.insubstantial) && ((! material.empty)) && material.isNotNamed(MC_WATER)) {
                length = dz;
                break;
            }
        }
        for (int dz = 0; dz < length; dz++) {
            if (dz == (length - 1)) {
                setWaterloggedBlock(world, x, y, height + dz, POINTED_DRIPSTONE_UP_TIP);
            } else if (dz == (length - 2)) {
                setWaterloggedBlock(world, x, y, height + dz, POINTED_DRIPSTONE_UP_FRUSTUM);
            } else if (dz == 0) {
                setWaterloggedBlock(world, x, y, height + dz, POINTED_DRIPSTONE_UP_BASE);
            } else {
                setWaterloggedBlock(world, x, y, height + dz, POINTED_DRIPSTONE_UP_MIDDLE);
            }
        }
    }

    private void renderStalactite(MinecraftWorld world, int x, int y, int height, int length, Random rng) {
        if ((height + 1) <= maxZ) {
            world.setMaterialAt(x, y, height + 1, DRIPSTONE_BLOCK);
        }
        for (int dz = 0; dz <= 2; dz++) {
            if ((height + dz) > maxZ) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (((dx != 0) || (dy != 0) || (dz != 1)) && (rng.nextInt(3) > 0)) {
                        final Material material = world.getMaterialAt(x + dx, y + dy, height + dz);
                        if (material.opaque && material.solid && material.natural && (material != DRIPSTONE_BLOCK)) {
                            world.setMaterialAt(x + dx, y + dy, height + dz, DRIPSTONE_BLOCK);
                        }
                    }
                }
            }
        }
        for (int dz = 0; dz < length; dz++) {
            final Material material = world.getMaterialAt(x, y, height - dz);
            if ((! material.insubstantial) && ((! material.empty))) {
                length = dz;
                break;
            }
        }
        for (int dz = 0; dz < length; dz++) {
            if (dz == (length - 1)) {
                setWaterloggedBlock(world, x, y, height - dz, POINTED_DRIPSTONE_DOWN_TIP);
            } else if (dz == (length - 2)) {
                setWaterloggedBlock(world, x, y, height - dz, POINTED_DRIPSTONE_DOWN_FRUSTUM);
            } else if (dz == 0) {
                setWaterloggedBlock(world, x, y, height - dz, POINTED_DRIPSTONE_DOWN_BASE);
            } else {
                setWaterloggedBlock(world, x, y, height - dz, POINTED_DRIPSTONE_DOWN_MIDDLE);
            }
        }
    }

    private void setWaterloggedBlock(MinecraftWorld world, int x, int y, int height, Material material) {
        world.setMaterialAt(x, y, height, material.withProperty(WATERLOGGED, world.getMaterialAt(x, y, height).isNamed(MC_WATER)));
    }

    private static int hashThreshold(int percent, int releaseDefault, int releaseThreshold) {
        return (percent == releaseDefault) ? releaseThreshold : Math.round(percent * 2.56f);
    }

    public static class CaveDecorationSettings implements java.io.Serializable, Cloneable {
        public CaveDecorationSettings() {
            enabledDecorations.put(Decoration.BROWN_MUSHROOM, null);
            enabledDecorations.put(Decoration.GLOW_LICHEN, null);
        }

        /**
         * Temporary simple constructor.
         */
        public CaveDecorationSettings(boolean brownMushrooms, boolean glowLichen, boolean lushCavePatches, boolean dripstoneCavePatches) {
            if (brownMushrooms) {
                enabledDecorations.put(Decoration.BROWN_MUSHROOM, null);
            }
            if (glowLichen) {
                enabledDecorations.put(Decoration.GLOW_LICHEN, null);
            }
            if (lushCavePatches) {
                enabledDecorations.put(LUSH_CAVE_PATCHES, null);
                noiseSettingsMap.put(LUSH_CAVE_PATCHES, new NoiseSettings(0L, 500, 1, 2.5f));
            }
            if (dripstoneCavePatches) {
                enabledDecorations.put(Decoration.DRIPSTONE_CAVE_PATCHES, null);
                noiseSettingsMap.put(Decoration.DRIPSTONE_CAVE_PATCHES, new NoiseSettings(1L, 500, 1, 2.5f));
            }
        }

        public boolean isEnabled(Decoration decoration) {
            return enabledDecorations.containsKey(decoration);
        }

        public int getLushThresholdOffset() { return lushThresholdOffset; }
        public void setLushThresholdOffset(int value) { lushThresholdOffset = Math.max(0, value); }
        public int getDripstoneThresholdOffset() { return dripstoneThresholdOffset; }
        public void setDripstoneThresholdOffset(int value) { dripstoneThresholdOffset = Math.max(0, value); }
        public int getMixedPatchChance() { return mixedPatchChance; }
        public void setMixedPatchChance(int value) { mixedPatchChance = Math.max(0, Math.min(100, value)); }
        public boolean isEnhancedLushFeatures() { return enhancedLushFeatures; }
        public void setEnhancedLushFeatures(boolean value) { enhancedLushFeatures = value; }
        public boolean isEnhancedDripstoneFeatures() { return enhancedDripstoneFeatures; }
        public void setEnhancedDripstoneFeatures(boolean value) { enhancedDripstoneFeatures = value; }
        public int getLushPoolFrequency() { return lushPoolFrequency; }
        public void setLushPoolFrequency(int value) { lushPoolFrequency = Math.max(0, Math.min(100, value)); }
        public int getLushPoolCellSize() { return lushPoolCellSize; }
        public void setLushPoolCellSize(int value) { lushPoolCellSize = Math.max(12, Math.min(48, value)); }
        public int getLushPoolMinRadius() { return lushPoolMinRadius; }
        public void setLushPoolMinRadius(int value) { lushPoolMinRadius = Math.max(2, Math.min(12, value)); }
        public int getLushPoolMaxRadius() { return lushPoolMaxRadius; }
        public void setLushPoolMaxRadius(int value) { lushPoolMaxRadius = Math.max(lushPoolMinRadius, Math.min(16, value)); }
        public int getLushPoolDryChance() { return lushPoolDryChance; }
        public void setLushPoolDryChance(int value) { lushPoolDryChance = Math.max(0, Math.min(100, value)); }
        public int getDripstonePatchCoverage() { return dripstonePatchCoverage; }
        public void setDripstonePatchCoverage(int value) { dripstonePatchCoverage = Math.max(0, Math.min(100, value)); }
        public int getSmallDripstoneFrequency() { return smallDripstoneFrequency; }
        public void setSmallDripstoneFrequency(int value) { smallDripstoneFrequency = Math.max(0, Math.min(100, value)); }
        public int getLargeDripstoneFrequency() { return largeDripstoneFrequency; }
        public void setLargeDripstoneFrequency(int value) { largeDripstoneFrequency = Math.max(0, Math.min(100, value)); }
        public int getLargeDripstoneCellSize() { return largeDripstoneCellSize; }
        public void setLargeDripstoneCellSize(int value) { largeDripstoneCellSize = Math.max(16, Math.min(64, value)); }
        public int getLargeDripstoneMaxRadius() { return largeDripstoneMaxRadius; }
        public void setLargeDripstoneMaxRadius(int value) { largeDripstoneMaxRadius = Math.max(4, Math.min(32, value)); }
        public int getLargeDripstoneSearchHeight() { return largeDripstoneSearchHeight; }
        public void setLargeDripstoneSearchHeight(int value) { largeDripstoneSearchHeight = Math.max(24, Math.min(192, value)); }
        public float getBiomePatchScale(Decoration decoration) {
            final NoiseSettings settings = noiseSettingsMap.get(decoration);
            return (settings != null) ? settings.getScale() : 1.0f;
        }
        public void setBiomePatchScale(Decoration decoration, float scale) {
            final NoiseSettings settings = noiseSettingsMap.get(decoration);
            if (settings != null) settings.setScale(Math.max(0.5f, scale));
        }

        public boolean isEnabledAt(Decoration decoration, int height) {
            if (enabledDecorations.containsKey(decoration)) {
                final int[] limits = enabledDecorations.get(decoration);
                return (limits == null) || ((height >= limits[0]) && (height <= limits[1]));
            } else {
                return false;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CaveDecorationSettings that = (CaveDecorationSettings) o;

            return (lushThresholdOffset == that.lushThresholdOffset)
                    && (dripstoneThresholdOffset == that.dripstoneThresholdOffset)
                    && (mixedPatchChance == that.mixedPatchChance)
                    && (lushPoolFrequency == that.lushPoolFrequency)
                    && (lushPoolCellSize == that.lushPoolCellSize)
                    && (lushPoolMinRadius == that.lushPoolMinRadius)
                    && (lushPoolMaxRadius == that.lushPoolMaxRadius)
                    && (lushPoolDryChance == that.lushPoolDryChance)
                    && (dripstonePatchCoverage == that.dripstonePatchCoverage)
                    && (smallDripstoneFrequency == that.smallDripstoneFrequency)
                    && (largeDripstoneFrequency == that.largeDripstoneFrequency)
                    && (largeDripstoneCellSize == that.largeDripstoneCellSize)
                    && (largeDripstoneMaxRadius == that.largeDripstoneMaxRadius)
                    && (largeDripstoneSearchHeight == that.largeDripstoneSearchHeight)
                    && (enhancedLushFeatures == that.enhancedLushFeatures)
                    && (enhancedDripstoneFeatures == that.enhancedDripstoneFeatures)
                    && enabledDecorations.equals(that.enabledDecorations)
                    && noiseSettingsMap.equals(that.noiseSettingsMap);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lushThresholdOffset, dripstoneThresholdOffset, mixedPatchChance,
                    lushPoolFrequency, lushPoolCellSize, lushPoolMinRadius, lushPoolMaxRadius, lushPoolDryChance,
                    dripstonePatchCoverage, smallDripstoneFrequency, largeDripstoneFrequency,
                    largeDripstoneCellSize, largeDripstoneMaxRadius, largeDripstoneSearchHeight,
                    enhancedLushFeatures, enhancedDripstoneFeatures, enabledDecorations, noiseSettingsMap);
        }

        @Override
        public CaveDecorationSettings clone() {
            try {
                return (CaveDecorationSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new MDCWrappingRuntimeException(e);
            }
        }

        /**
         * If the key is present, the decoration is enabled. If the value is {@code null}, it is enabled everywhere;
         * otherwise the value is an array with the minimum and maximum levels at which to apply the decoration.
         */
        private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (settingsVersion < 1) {
                lushPoolFrequency = 63;
                lushPoolCellSize = 20;
                lushPoolMinRadius = 4;
                lushPoolMaxRadius = 8;
                lushPoolDryChance = 13;
                dripstonePatchCoverage = 50;
                smallDripstoneFrequency = 25;
                largeDripstoneFrequency = 56;
                largeDripstoneCellSize = 32;
                largeDripstoneMaxRadius = 19;
                largeDripstoneSearchHeight = 96;
                settingsVersion = 1;
            }
        }

        private int lushThresholdOffset, dripstoneThresholdOffset, mixedPatchChance = 100;
        private int lushPoolFrequency = 63, lushPoolCellSize = 20, lushPoolMinRadius = 4, lushPoolMaxRadius = 8, lushPoolDryChance = 13;
        private int dripstonePatchCoverage = 50, smallDripstoneFrequency = 25, largeDripstoneFrequency = 56;
        private int largeDripstoneCellSize = 32, largeDripstoneMaxRadius = 19, largeDripstoneSearchHeight = 96;
        private boolean enhancedLushFeatures, enhancedDripstoneFeatures;
        private int settingsVersion = 1;
        final Map<Decoration, int[]> enabledDecorations = new HashMap<>();
        final Map<Decoration, NoiseSettings> noiseSettingsMap = new HashMap<>();

        private static final long serialVersionUID = 1L;

        public enum Decoration {
            BROWN_MUSHROOM, GLOW_LICHEN, LUSH_CAVE_PATCHES, DRIPSTONE_CAVE_PATCHES
        }
    }

    static class State {
        long seed;
        Tile tile;
        int maxY, waterLevel;
        boolean glassCeiling, breachedCeiling, surfaceBreaking, leaveWater, previousBlockInCavern, floodWithLava;
    }

    protected final boolean decorationEnabled;

    private final CaveDecorationSettings decorationSettings;
    private final NoiseHeightMap lushCaveNoise, dripstoneCaveNoise, lushPoolNoise, dripstoneFeatureNoise, dripstonePatchNoise;
    private final BiomeUtils biomeUtils;
    private final boolean decorateBrownMushrooms, decorateGlowLichen, decorateLushCaves, decorateDripstoneCaves, setBiomes;

    private static final ThreadLocal<State> STATE_HOLDER = new ThreadLocal<>();
    private static final int MUSHROOM_CHANCE = 250;
    private static final float LUSH_CAVE_THRESHOLD = 600;
    private static final float DRIPSTONE_CAVE_THRESHOLD = 675;
    private static final Set<String> SUPPORTS_DRIPSTONE = ImmutableSet.of(MC_BEDROCK, MC_STONE, MC_GRANITE, MC_ANDESITE, MC_DIORITE, MC_CALCITE, MC_BASALT, MC_DEEPSLATE,
            MC_COAL_ORE, MC_IRON_ORE, MC_GOLD_ORE, MC_REDSTONE_ORE, MC_DIAMOND_ORE, MC_LAPIS_ORE, MC_EMERALD_ORE, MC_NETHER_QUARTZ_ORE, MC_COPPER_ORE,
            MC_DEEPSLATE_COAL_ORE, MC_DEEPSLATE_IRON_ORE, MC_DEEPSLATE_GOLD_ORE, MC_DEEPSLATE_REDSTONE_ORE, MC_DEEPSLATE_DIAMOND_ORE, MC_DEEPSLATE_LAPIS_ORE, MC_DEEPSLATE_EMERALD_ORE, MC_DEEPSLATE_COPPER_ORE);
}