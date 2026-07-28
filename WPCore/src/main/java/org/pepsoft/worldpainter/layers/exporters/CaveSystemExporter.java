/*
 * WorldPainter Languages, an unofficial localization fork of WorldPainter
 * (https://github.com/saplome/WorldPainter-LANGUAGES).
 * Copyright © 2026 saplome
 * Licensed under the GNU General Public License, version 3.
 */
package org.pepsoft.worldpainter.layers.exporters;

import com.google.common.collect.ImmutableSet;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.PerlinNoise;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.NoiseSettings;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.Fixup;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.layers.CaveSystem;
import org.pepsoft.worldpainter.heightMaps.NoiseHeightMap;

import java.awt.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singleton;
import static org.pepsoft.worldpainter.exporting.SecondPassLayerExporter.Stage.ADD_FEATURES;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.exporting.SecondPassLayerExporter.Stage.CARVE;

public final class CaveSystemExporter extends AbstractCavesExporter<CaveSystem>
        implements SecondPassLayerExporter {
    public CaveSystemExporter(Dimension dimension, Platform platform, ExporterSettings settings) {
        super(dimension, platform,
                (settings instanceof CaveSystemSettings) ? (CaveSystemSettings) settings : new CaveSystemSettings(),
                CaveSystem.INSTANCE);
        caveSettings = (CaveSystemSettings) super.settings;
        runtime = new RuntimeConfig(caveSettings);
        if (runtime.experimental && caveSettings.isLushCaves()) {
            final long gateSeed = dimension.getSeed() + stableHash("minecraft:lush_caves");
            final float regionSize = Math.max(0.25f, Math.min(16.0f,
                    2.0f * caveSettings.getLushRegionScale() / 80.0f));
            pluginLushCheeseNoise = new NoiseHeightMap(new NoiseSettings(0L, 500, 1,
                    10.0f * regionSize), gateSeed);
            final double rarity = Math.max(0.002, Math.min(0.35,
                    0.03 * 105.0 / Math.max(1, caveSettings.getLushRarity())));
            pluginLushCheeseThreshold = calibrateThreshold(pluginLushCheeseNoise, rarity, gateSeed);
        } else {
            pluginLushCheeseNoise = null;
            pluginLushCheeseThreshold = Double.POSITIVE_INFINITY;
        }
    }

    @Override public Set<Stage> getStages() { return decorationEnabled ? ImmutableSet.of(CARVE, ADD_FEATURES) : singleton(CARVE); }

    @Override
    public List<Fixup> carve(Rectangle area, Rectangle exportedArea, MinecraftWorld minecraftWorld) {
        final CaveSystemSettings settings = (CaveSystemSettings) super.settings;
        final int minimumLevel = settings.getMinimumLevel();
        final int minY = Math.max(settings.getMinimumY(), minHeight + (dimension.isBottomless() ? 0 : 1));
        final long seed = dimension.getSeed();
        excavatedBlocks.clear();
        lushMegaBlocks.clear();
        dripstoneMegaBlocks.clear();
        lushCheeseBlocks.clear();
        seedNoises(seed);
        visitChunksForLayerInAreaForEditing(minecraftWorld, layer, area, dimension, (tile, chunkX, chunkZ, chunkSupplier) -> {
            final Chunk chunk = chunkSupplier.get();
            final int xOffset = (chunkX & 7) << 4, zOffset = (chunkZ & 7) << 4;
            final int columnLength = Math.max(1, maxZ - minY + 1);
            final Material[] originals = new Material[columnLength];
            final boolean[] carvedBySystem = new boolean[columnLength];
            final int[] runBottom = new int[columnLength], runTop = new int[columnLength];
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                final int localX = xOffset + x, localZ = zOffset + z;
                if (tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, localX, localZ)) continue;
                final int intensity = Math.max(minimumLevel,
                        tile.getLayerValue(CaveSystem.INSTANCE, localX, localZ));
                if (intensity <= 0) continue;
                final int worldX = tile.getX() * TILE_SIZE + localX, worldZ = tile.getY() * TILE_SIZE + localZ;
                final int surfaceY = Math.min(tile.getIntHeight(localX, localZ), maxZ);
                final int roofDepth = Math.max(dimension.getTopLayerDepth(worldX, worldZ, surfaceY), 3);
                final int maxYHere = settings.isSurfaceBreaking() ? surfaceY : surfaceY - roofDepth;
                Arrays.fill(originals, null);
                Arrays.fill(carvedBySystem, false);
                final DensityColumn column = createColumn(worldX, worldZ, surfaceY, minY, intensity);
                final int fluidData = fluidAt(tile.getWaterLevel(localX, localZ), worldX, worldZ, minY, maxYHere);
                final int fluidLevel = fluidData >> 1;
                final boolean fluidLava = (fluidData & 1) != 0;
                setupForColumn(seed, tile, maxZ, fluidLevel, false, settings.isSurfaceBreaking(),
                        settings.isLeaveWater(), fluidLava);
                boolean pluginCheesePresent = false;
                final boolean pluginLushCheeseColumn = pluginLushCheeseNoise != null
                        && pluginLushCheeseNoise.getValue(worldX, worldZ) >= pluginLushCheeseThreshold;
                for (int y = maxYHere; y >= minY; y--) {
                    final Material originalMaterial = chunk.getMaterial(x, y, z);
                    if (originalMaterial.empty) { emptyBlockEncountered(); continue; }
                    originals[y - minY] = originalMaterial;
                    final int type = caveType(worldX, y, worldZ, column);
                    if (runtime.experimental && (type == CAVE_CHEESE)) pluginCheesePresent = true;
                    carvedBySystem[y - minY] = type != CAVE_NONE;
                    if ((type != CAVE_NONE) && area.contains(worldX, worldZ)) {
                        final int blockIndex = (worldX - area.x) + (worldZ - area.y) * area.width
                                + (y - minHeight) * area.width * area.height;
                        excavatedBlocks.set(blockIndex);
                        if (column.lastMegaBiome == MEGA_BIOME_LUSH) lushMegaBlocks.set(blockIndex);
                        else if (column.lastMegaBiome == MEGA_BIOME_DRIPSTONE) dripstoneMegaBlocks.set(blockIndex);
                    }
                    final boolean allowFluid = fluidLava
                            ? (((type == CAVE_BACKBONE) && settings.isLavaInBackbone())
                                    || (((type == CAVE_CHEESE) || (type == CAVE_GRAND)) && settings.isLavaInChambers()))
                            : (((type == CAVE_CHEESE) || (type == CAVE_GRAND)) && settings.isWaterInChambers());
                    if ((type == CAVE_CHEESE) && pluginLushCheeseColumn
                            && ((!allowFluid) || (y > fluidLevel)) && area.contains(worldX, worldZ)) {
                        final int blockIndex = (worldX - area.x) + (worldZ - area.y) * area.width
                                + (y - minHeight) * area.width * area.height;
                        lushCheeseBlocks.set(blockIndex);
                    }
                    processBlock(chunk, x, y, z, type != CAVE_NONE, allowFluid);
                }
                if (runtime.experimental && pluginCheesePresent) {
                    fillPluginPillarColumn(chunk, x, z, worldX, worldZ, minY, maxYHere,
                            originals, carvedBySystem, runBottom, runTop, area);
                }
                resetColumn();
            }
            return true;
        });
        return null;
    }

    @Override
    public List<Fixup> addFeatures(Rectangle area, Rectangle exportedArea, MinecraftWorld minecraftWorld) {
        final Random random = new Random(dimension.getSeed() + exportedArea.x * 65537L + exportedArea.y);
        final int width = area.width, height = area.height, layerSize = width * height;
        for (int blockIndex = excavatedBlocks.nextSetBit(0); blockIndex >= 0;
             blockIndex = excavatedBlocks.nextSetBit(blockIndex + 1)) {
            final int horizontalIndex = blockIndex % layerSize;
            final int localX = horizontalIndex % width, localZ = horizontalIndex / width;
            if ((localX > 0) && (localX < width - 1) && (localZ > 0) && (localZ < height - 1)
                    && excavatedBlocks.get(blockIndex - 1) && excavatedBlocks.get(blockIndex + 1)
                    && excavatedBlocks.get(blockIndex - width) && excavatedBlocks.get(blockIndex + width)
                    && excavatedBlocks.get(blockIndex - layerSize) && excavatedBlocks.get(blockIndex + layerSize)) {
                continue;
            }
            final int y = (blockIndex / layerSize) + minHeight;
            final int z = localZ + area.y;
            final int x = localX + area.x;
            final int forcedBiome = lushMegaBlocks.get(blockIndex) ? MEGA_BIOME_LUSH
                    : (dripstoneMegaBlocks.get(blockIndex) ? MEGA_BIOME_DRIPSTONE
                    : (lushCheeseBlocks.get(blockIndex) ? MEGA_BIOME_LUSH : MEGA_BIOME_NORMAL));
            decorateBlock(minecraftWorld, random, x, z, y, forcedBiome);
        }
        return null;
    }

    private DensityColumn createColumn(int x, int z, int surfaceY, int minY, int intensity) {
        final float openingWeight;
        if (runtime.openingFrequency <= 0) openingWeight = 0.0f;
        else if (runtime.openingFrequency >= 100) openingWeight = 1.0f;
        else {
            final float selector = sample(surfaceSelector, x, 0.0, z, runtime.openingScale, runtime.openingScale);
            openingWeight = smoothstep(runtime.openingThreshold - runtime.openingBand,
                    runtime.openingThreshold + runtime.openingBand, selector);
        }
        final int r = 4;
        final int hWest = dimension.getIntHeightAt(x - r, z), hEast = dimension.getIntHeightAt(x + r, z);
        final int hNorth = dimension.getIntHeightAt(x, z - r), hSouth = dimension.getIntHeightAt(x, z + r);
        final float gradientX = (hEast - hWest) / (float) (r * 2);
        final float gradientZ = (hSouth - hNorth) / (float) (r * 2);
        final int[] haloHeights = new int[16];
        final float[] haloDistances = new float[16];
        int i = 0;
        for (int radius: new int[] {2, 4}) {
            for (int dx: new int[] {-radius, 0, radius}) for (int dz: new int[] {-radius, 0, radius}) {
                if ((dx == 0) && (dz == 0)) continue;
                haloHeights[i] = dimension.getIntHeightAt(x + dx, z + dz);
                haloDistances[i] = (float) Math.sqrt(dx * dx + dz * dz);
                i++;
            }
        }
        return new DensityColumn(surfaceY, minY, intensity / 15.0f, openingWeight,
                gradientX, gradientZ, haloHeights, haloDistances, createMegaRegions(x, z, minY));
    }

    private int caveType(int x, int y, int z, DensityColumn column) {
        final int depth = column.surfaceY - y, bottomDistance = y - column.minY;
        if ((depth < 0) || (bottomDistance < 2)) return CAVE_NONE;
        final float terrainDistance = column.terrainDistance(y);
        if (terrainDistance < -0.25f) return CAVE_NONE;
        final float openingFade = 1.0f - smoothstep(runtime.openingDepth,
                runtime.openingDepth + runtime.boundaryFade, depth);
        final float effectiveOpeningWeight = clamp(column.openingWeight * openingFade
                * runtime.openingStrength, 0.0f, 1.0f);
        final float boundary = boundaryWeight(x, y, z, terrainDistance, bottomDistance, effectiveOpeningWeight);
        if (boundary <= 0.001f) return CAVE_NONE;
        final float closedBoundary = runtime.needsClosedBoundary
                ? boundaryWeight(x, y, z, terrainDistance, bottomDistance, 0.0f) : boundary;

        final float[] noise = noiseValues(x, y, z, column);
        final float intensityPenalty = (1.0f - column.intensity) * 0.22f;
        final float boundaryPenalty = (1.0f - boundary) * 0.48f;
        final float closedBoundaryPenalty = (1.0f - closedBoundary) * 0.48f;

        column.lastMegaBiome = MEGA_BIOME_NORMAL;
        float mega = NEGATIVE;
        if (runtime.grandEnabled) {
            final float familyBoundary = runtime.openGrand ? boundaryPenalty : closedBoundaryPenalty;
            mega = megaCavernDensity(x, y, z, column) - familyBoundary * 0.72f;
        }

        float cheese = NEGATIVE;
        if (runtime.cheeseEnabled) {
            if (runtime.experimental) {
                final float familyBoundary = runtime.openCheese ? boundary : closedBoundary;
                final double amountThreshold = 0.1 + 0.5 * runtime.pluginCheeseAmount;
                final double t0 = Math.min(1.0, amountThreshold * (1.0 + column.intensity * 0.3));
                if (t0 > 0.0) {
                    final double n = pluginCheeseNoise(x, y, z);
                    final double carveness = (2.0 * t0 - 1.0 - n) / (2.0 * t0);
                    cheese = (float) carveness - (1.0f - familyBoundary);
                }
            } else {
                final float familyBoundary = runtime.openCheese ? boundaryPenalty : closedBoundaryPenalty;
                cheese = noise[N_CHEESE] + noise[N_DETAIL] + runtime.globalDensityOffset
                        - (runtime.cheeseThreshold + intensityPenalty + familyBoundary);
            }
        }

        float grand = NEGATIVE;
        if (runtime.grandEnabled) {
            final float familyBoundary = runtime.openGrand ? boundaryPenalty : closedBoundaryPenalty;
            grand = noise[N_GRAND] + runtime.globalDensityOffset
                    - (runtime.grandThreshold + intensityPenalty + familyBoundary);
        }

        float spaghetti = NEGATIVE;
        if (runtime.spaghettiEnabled) {
            final float familyBoundary = runtime.openSpaghetti ? boundaryPenalty : closedBoundaryPenalty;
            spaghetti = runtime.spaghettiBase + runtime.globalDensityOffset
                    - Math.max(Math.abs(noise[N_SPAGHETTI_A]), Math.abs(noise[N_SPAGHETTI_B]))
                    - intensityPenalty * 0.35f - familyBoundary * 0.50f;
        }

        float backbone = NEGATIVE;
        if (runtime.backboneEnabled) {
            final float familyBoundary = runtime.openBackbone ? boundaryPenalty : closedBoundaryPenalty;
            backbone = runtime.backboneBase + runtime.globalDensityOffset
                    - Math.max(Math.abs(noise[N_BACKBONE_A]), Math.abs(noise[N_BACKBONE_B]))
                    - intensityPenalty * 0.35f - familyBoundary * 0.50f;
        }

        float noodle = NEGATIVE;
        if (runtime.noodleEnabled) {
            final float familyBoundary = runtime.openNoodles ? boundaryPenalty : closedBoundaryPenalty;
            noodle = runtime.noodleBase + runtime.globalDensityOffset
                    - Math.max(Math.abs(noise[N_NOODLE_A]), Math.abs(noise[N_NOODLE_B]))
                    - intensityPenalty * 0.40f - familyBoundary * 0.55f;
        }

        float combined = smoothMax(mega, cheese, runtime.unionSmoothness);
        combined = smoothMax(combined, grand, runtime.unionSmoothness);
        combined = smoothMax(combined, spaghetti, runtime.unionSmoothness);
        combined = smoothMax(combined, backbone, runtime.unionSmoothness);
        combined = smoothMax(combined, noodle, runtime.unionSmoothness);
        if (combined <= 0.0f) { column.lastMegaBiome = MEGA_BIOME_NORMAL; return CAVE_NONE; }
        if ((mega >= cheese) && (mega >= grand) && (mega >= spaghetti) && (mega >= backbone) && (mega >= noodle)) return CAVE_GRAND;
        column.lastMegaBiome = MEGA_BIOME_NORMAL;
        if ((grand >= cheese) && (grand >= spaghetti) && (grand >= backbone) && (grand >= noodle)) return CAVE_GRAND;
        if ((cheese >= spaghetti) && (cheese >= backbone) && (cheese >= noodle)) return CAVE_CHEESE;
        if (backbone >= Math.max(spaghetti, noodle)) return CAVE_BACKBONE;
        return CAVE_TUNNEL;
    }

    private float[] noiseValues(int x, int y, int z, DensityColumn column) {
        if ((y & 1) == 0) return noiseFrame(x, y, z, column);
        final float[] upper = noiseFrame(x, y + 1, z, column);
        for (int i = 0; i < NOISE_VALUE_COUNT; i++) column.interpolated[i] = upper[i] * 0.5f;
        final float[] lower = noiseFrame(x, y - 1, z, column);
        for (int i = 0; i < NOISE_VALUE_COUNT; i++) column.interpolated[i] += lower[i] * 0.5f;
        return column.interpolated;
    }

    private float[] noiseFrame(int x, int y, int z, DensityColumn column) {
        if (column.frameAY == y) return column.frameA;
        if (column.frameBY == y) return column.frameB;
        final float[] target;
        if (column.frameAY == Integer.MIN_VALUE) {
            column.frameAY = y; target = column.frameA;
        } else if (column.frameBY == Integer.MIN_VALUE) {
            column.frameBY = y; target = column.frameB;
        } else if (Math.abs(column.frameAY - y) >= Math.abs(column.frameBY - y)) {
            column.frameAY = y; target = column.frameA;
        } else {
            column.frameBY = y; target = column.frameB;
        }
        fillNoiseFrame(x, y, z, column, target);
        return target;
    }

    private void fillNoiseFrame(int x, int y, int z, DensityColumn column, float[] target) {
        warpedCoordinates(x, y, z, column.warped);
        prepareCoordinates(column.warped[0], column.warped[1], column.warped[2], column.prepared);
        if (runtime.experimental || !runtime.cheeseEnabled) {
            target[N_CHEESE] = target[N_DETAIL] = 0.0f;
        } else {
            target[N_CHEESE] = fbmPrepared(cheeseA, cheeseB, column.prepared, runtime.cheeseH, runtime.cheeseV);
            target[N_DETAIL] = samplePrepared(detailNoise, column.prepared, 31.0f, 27.0f) * runtime.detailFactor;
        }
        target[N_GRAND] = runtime.grandEnabled
                ? fbmWorld(grandA, grandB, column.warped[0], column.warped[1], column.warped[2], runtime.grandH, runtime.grandV) : 0.0f;
        if (runtime.spaghettiEnabled) {
            target[N_SPAGHETTI_A] = samplePrepared(spaghettiA, column.prepared, runtime.spaghettiH, 48.0f);
            target[N_SPAGHETTI_B] = samplePrepared(spaghettiB, column.prepared, runtime.spaghettiH, 48.0f);
        } else { target[N_SPAGHETTI_A] = target[N_SPAGHETTI_B] = 0.0f; }
        if (runtime.backboneEnabled) {
            target[N_BACKBONE_A] = samplePrepared(backboneA, column.prepared, runtime.backboneH, 64.0f);
            target[N_BACKBONE_B] = samplePrepared(backboneB, column.prepared, runtime.backboneH, 64.0f);
        } else { target[N_BACKBONE_A] = target[N_BACKBONE_B] = 0.0f; }
        if (runtime.noodleEnabled) {
            target[N_NOODLE_A] = samplePrepared(noodleA, column.prepared, runtime.noodleH, 37.0f);
            target[N_NOODLE_B] = samplePrepared(noodleB, column.prepared, runtime.noodleH, 37.0f);
        } else { target[N_NOODLE_A] = target[N_NOODLE_B] = 0.0f; }
    }

    private double pluginCheeseNoise(int x, int y, int z) {
        final double f = (1.0 / 48.0) / runtime.pluginCheeseSize;
        final double vertical = 1.7 * runtime.pluginCheeseVerticalMultiplier;
        final double a = pluginCheeseA.fractal(4, x * f, y * f * vertical, z * f,
                runtime.pluginCheesePersistence);
        final double b = pluginCheeseB.fractal(4, x * f * 1.13, y * f * vertical * 1.07,
                z * f * 1.13, runtime.pluginCheesePersistence);
        return 0.5 * (a - b);
    }

    private void fillPluginPillarColumn(Chunk chunk, int localX, int localZ, int worldX, int worldZ,
                                        int minY, int maxY, Material[] originals, boolean[] carved,
                                        int[] runBottom, int[] runTop, Rectangle area) {
        final PluginPillarColumn pillar = pluginPillarAt(worldX, worldZ);
        if (pillar == null || maxY < minY) return;
        int currentBottom = Integer.MIN_VALUE;
        for (int y = minY; y <= maxY; y++) {
            final int index = y - minY;
            if (carved[index]) {
                if (currentBottom == Integer.MIN_VALUE) currentBottom = y;
                runBottom[index] = currentBottom;
            } else {
                currentBottom = Integer.MIN_VALUE;
                runBottom[index] = Integer.MIN_VALUE;
            }
        }
        int currentTop = Integer.MIN_VALUE;
        for (int y = maxY; y >= minY; y--) {
            final int index = y - minY;
            if (carved[index]) {
                if (currentTop == Integer.MIN_VALUE) currentTop = y;
                runTop[index] = currentTop;
            } else {
                currentTop = Integer.MIN_VALUE;
                runTop[index] = Integer.MIN_VALUE;
            }
        }
        final int range = Math.max(1, (int) Math.round(6.0 * runtime.pluginCheeseSize));
        for (int y = maxY; y >= minY; y--) {
            final int index = y - minY;
            if (!carved[index]) continue;
            final Material originalMaterial = originals[index];
            if (!isRestorablePillarMaterial(originalMaterial)) continue;
            final int distDown = Math.min(y - runBottom[index] + 1, range);
            final int distUp = Math.min(runTop[index] - y + 1, range);
            if (!pillar.shouldFill(y, distDown, distUp, range)) continue;
            chunk.setMaterial(localX, y, localZ, originalMaterial);
            carved[index] = false;
            if (area.contains(worldX, worldZ)) {
                final int blockIndex = (worldX - area.x) + (worldZ - area.y) * area.width
                        + (y - minHeight) * area.width * area.height;
                excavatedBlocks.clear(blockIndex);
                lushMegaBlocks.clear(blockIndex);
                dripstoneMegaBlocks.clear(blockIndex);
                lushCheeseBlocks.clear(blockIndex);
            }
        }
    }

    private PluginPillarColumn pluginPillarAt(int worldX, int worldZ) {
        final double density = 0.35;
        final double f = (1.0 / 48.0) / runtime.pluginCheeseSize;
        final double pillarFrequency = f * 12.0;
        final CaveSystemWorleyNoise3D.Cell cell = pluginPillarField.nearest(
                worldX * pillarFrequency, worldZ * pillarFrequency, 0.0);
        if (cell.value >= density) return null;
        final double baseRadius = 0.1 + 0.18 * fract(cell.value * 7.13 + 0.5);
        final double maximumRadius = Math.min(0.85, baseRadius * 1.7 * 1.371);
        if (cell.f1 >= maximumRadius) return null;
        return new PluginPillarColumn(worldX, worldZ, cell.f1, baseRadius, pillarFrequency);
    }

    private static boolean isRestorablePillarMaterial(Material material) {
        return material != null && !material.empty && material.solid
                && !material.isNamedOneOf("minecraft:water", "minecraft:lava")
                && !material.isNamed("minecraft:bedrock");
    }

    private static double fract(double value) {
        final double result = value - Math.floor(value);
        return result < 0.0 ? result + 1.0 : result;
    }

    private final class PluginPillarColumn {
        PluginPillarColumn(int worldX, int worldZ, double cellDistance,
                           double baseRadius, double pillarFrequency) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.cellDistance = cellDistance;
            this.baseRadius = baseRadius;
            this.pillarFrequency = pillarFrequency;
        }

        boolean shouldFill(int y, int distDown, int distUp, int range) {
            final int verticalClearance = Math.min(Math.min(distUp, distDown), range);
            final double depth = Math.max(0.0, Math.min(1.0, verticalClearance / (double) range));
            final double flare = 1.7 - 1.15 * depth;
            final double radiusBase = baseRadius * flare;
            final double radiusLow = Math.max(0.06, radiusBase * 0.629);
            final double radiusHigh = Math.min(0.85, radiusBase * 1.371);
            if (cellDistance < radiusLow) return true;
            if (cellDistance >= radiusHigh) return false;
            final double detail = pluginPillarDetail.fractal(4,
                    worldX * pillarFrequency, y * pillarFrequency, worldZ * pillarFrequency, 0.5);
            final double radius = Math.max(0.06, Math.min(0.85, radiusBase * (1.0 + 0.35 * detail)));
            return cellDistance < radius;
        }

        final int worldX, worldZ;
        final double cellDistance, baseRadius, pillarFrequency;
    }

        private float megaCavernDensity(int x, int y, int z, DensityColumn column) {
        float result = NEGATIVE, strongest = NEGATIVE;
        int strongestBiome = MEGA_BIOME_NORMAL;
        boolean sharedReady = false;
        double warpDx = 0.0, warpDz = 0.0;
        float warpDyUnit = 0.0f, macro = 0.0f, pocket = 0.0f;
        float positiveIrregularity = 0.0f, ribPenalty = 0.0f, pillarPenalty = 0.0f;
        for (MegaRegion region: column.megaRegions) {
            if ((!region.active) || (column.intensity + 0.0001f < region.minimumIntensity)) continue;
            final double dx = x - region.centreX, dz = z - region.centreZ, dy = y - region.centreY;
            if ((dx * dx + dz * dz > region.horizontalReachSquared)
                    || (Math.abs(dy) > region.verticalReach)) continue;
            if (!sharedReady) {
                warpDx = warpX.getPerlinNoise(x / 96.0f, z / 96.0f, y / 72.0f) * 24.0;
                warpDyUnit = warpY.getPerlinNoise((x + 313) / 112.0f, (z - 179) / 112.0f, y / 84.0f);
                warpDz = warpZ.getPerlinNoise((x - 227) / 96.0f, (z + 401) / 96.0f, y / 72.0f) * 24.0;
                macro = grandA.getPerlinNoise(x / 68.0f, z / 68.0f, y / 52.0f) * 0.38f
                        + grandB.getPerlinNoise((x + 619) / 34.0f, (z - 421) / 34.0f, y / 29.0f) * 0.16f;
                pocket = cheeseA.getPerlinNoise((x - 97) / 43.0f, (z + 151) / 43.0f, y / 36.0f) * 0.14f;
                positiveIrregularity = Math.abs(detailNoise.getPerlinNoise(
                        (x + 43) / 31.0f, (z - 67) / 31.0f, y / 21.0f)) * 0.18f;
                final float ribNoise = boundaryNoise.getPerlinNoise(x / 47.0f, z / 47.0f, y / 18.0f);
                final float rib = 0.055f - Math.abs(ribNoise);
                ribPenalty = (rib > 0.0f) ? rib * 3.8f : 0.0f;
                final float pillarA = spaghettiA.getPerlinNoise(x / 52.0f, z / 52.0f, y / 230.0f);
                final float pillarB = spaghettiB.getPerlinNoise((x + 173) / 52.0f, (z - 211) / 52.0f, y / 230.0f);
                final float pillar = 0.092f - Math.max(Math.abs(pillarA), Math.abs(pillarB));
                pillarPenalty = (pillar > 0.0f) ? pillar * 6.5f : 0.0f;
                sharedReady = true;
            }
            float density = irregularPocketDensity(x, y, z,
                    region.centreX, region.centreY, region.centreZ,
                    region.radiusX, region.radiusY, region.radiusZ, region.cos, region.sin,
                    warpDx, warpDyUnit, warpDz, macro, pocket, positiveIrregularity, ribPenalty);
            density = smoothMax(density, irregularPocketDensity(x, y, z,
                    region.centreX + region.lobeAX, region.centreY + region.lobeAY, region.centreZ + region.lobeAZ,
                    region.radiusX * 0.68f, region.radiusY * region.lobeAVerticalScale, region.radiusZ * 0.72f,
                    region.cos, region.sin, warpDx, warpDyUnit, warpDz, macro, pocket, positiveIrregularity, ribPenalty), 0.075f);
            density = smoothMax(density, irregularPocketDensity(x, y, z,
                    region.centreX + region.lobeBX, region.centreY + region.lobeBY, region.centreZ + region.lobeBZ,
                    region.radiusX * 0.54f, region.radiusY * region.lobeBVerticalScale, region.radiusZ * 0.62f,
                    region.cosB, region.sinB, warpDx, warpDyUnit, warpDz, macro, pocket, positiveIrregularity, ribPenalty), 0.075f);
            density -= pillarPenalty;
            if (density > strongest) { strongest = density; strongestBiome = region.biomeType; }
            result = smoothMax(result, density, 0.065f);
        }
        column.lastMegaBiome = (result > 0.0f) ? strongestBiome : MEGA_BIOME_NORMAL;
        return result;
    }

    private float irregularPocketDensity(double x, double y, double z,
                                           double centreX, double centreY, double centreZ,
                                           float radiusX, float radiusY, float radiusZ,
                                           float cos, float sin, double warpDx, float warpDyUnit,
                                           double warpDz, float macro, float pocket,
                                           float positiveIrregularity, float ribPenalty) {
        final double wx = x + warpDx;
        final double wy = y + warpDyUnit * Math.min(18.0f, radiusY * 0.24f);
        final double wz = z + warpDz;
        final double dx = wx - centreX, dz = wz - centreZ;
        final double localX = cos * dx + sin * dz;
        final double localZ = -sin * dx + cos * dz;
        final double nx = localX / radiusX, ny = (wy - centreY) / radiusY, nz = localZ / radiusZ;
        final float distance = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float density = 0.80f - distance + macro + pocket - positiveIrregularity;
        if (ribPenalty > 0.0f) density -= ribPenalty;
        return density;
    }

    private MegaRegion[] createMegaRegions(int x, int z, int minY) {
        final int cellX = Math.floorDiv(x, MEGA_CELL_SIZE), cellZ = Math.floorDiv(z, MEGA_CELL_SIZE);
        final long neighbourhoodKey = mix64((((long) cellX) << 32) ^ (cellZ & 0xffffffffL)
                ^ ((long) minY * 0xD6E8FEB86659FD93L) ^ 0xBB67AE8584CAA73BL);
        return megaNeighbourhoodCache.computeIfAbsent(neighbourhoodKey, ignored -> {
            final MegaRegion[] regions = new MegaRegion[18];
            int index = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                final int regionX = cellX + dx, regionZ = cellZ + dz;
                for (int candidate = 0; candidate < 2; candidate++) {
                    final int variant = candidate;
                    final long key = mix64((((long) regionX) << 32) ^ (regionZ & 0xffffffffL)
                            ^ ((long) minY * 0xD6E8FEB86659FD93L)
                            ^ ((long) variant * 0xA24BAED4963EE407L));
                    regions[index++] = megaRegionCache.computeIfAbsent(key,
                            ignoredRegion -> createMegaRegion(regionX, regionZ, minY, variant));
                }
            }
            return regions;
        });
    }

    private MegaRegion createMegaRegion(int cellX, int cellZ, int minY, int candidate) {
        long h = mix64(seededFor ^ ((long) cellX * 0x9E3779B97F4A7C15L)
                ^ ((long) cellZ * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) candidate * 0xA24BAED4963EE407L) ^ 0x6A09E667F3BCC909L);
        final boolean active = Math.floorMod(h, 100L) < runtime.megaFrequency;
        h = mix64(h + 0x9E3779B97F4A7C15L);
        final double centreX = cellX * (double) MEGA_CELL_SIZE + MEGA_CELL_SIZE * (0.25 + unit(h) * 0.50);
        h = mix64(h + 0x9E3779B97F4A7C15L);
        final double centreZ = cellZ * (double) MEGA_CELL_SIZE + MEGA_CELL_SIZE * (0.25 + unit(h) * 0.50);
        final int localSurface = dimension.getIntHeightAt((int) Math.round(centreX), (int) Math.round(centreZ));
        final float floorY = minY + 8.0f;
        final float ceilingY = localSurface - Math.max(10.0f, runtime.surfaceClearance);
        final float available = Math.max(1.0f, ceilingY - floorY);

        h = mix64(h + 0x9E3779B97F4A7C15L); final float tallRoll = unit(h);
        h = mix64(h + 0x9E3779B97F4A7C15L); final float heightRoll = unit(h);
        final float desiredHeight = ((tallRoll * 100.0f) < runtime.megaTallChance)
                ? (88.0f + heightRoll * 72.0f) * runtime.megaVerticalScale
                : (46.0f + heightRoll * 64.0f) * runtime.megaVerticalScale;
        final float radiusY = Math.max(12.0f, Math.min(Math.max(24.0f * runtime.megaVerticalScale, desiredHeight * 0.5f), available * 0.42f));
        h = mix64(h + 0x9E3779B97F4A7C15L); final float verticalFreedom = Math.max(0.0f, available - radiusY * 2.0f);
        final double centreY = floorY + radiusY + unit(h) * verticalFreedom;

        h = mix64(h + 0x9E3779B97F4A7C15L); final float radiusX = (48.0f + unit(h) * 47.0f) * runtime.megaHorizontalScale;
        h = mix64(h + 0x9E3779B97F4A7C15L); final float radiusZ = (42.0f + unit(h) * 43.0f) * runtime.megaHorizontalScale;
        h = mix64(h + 0x9E3779B97F4A7C15L); final float angle = unit(h) * (float) (Math.PI * 2.0);
        final float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        h = mix64(h + 0x9E3779B97F4A7C15L); final float minimumIntensity = 0.18f + unit(h) * 0.42f;

        h = mix64(h + 0x9E3779B97F4A7C15L); final float aSide = (unit(h) - 0.5f) * radiusZ * 0.72f;
        h = mix64(h + 0x9E3779B97F4A7C15L); final float aForward = radiusX * (0.36f + unit(h) * 0.30f);
        h = mix64(h + 0x9E3779B97F4A7C15L); final float lobeAY = (unit(h) - 0.5f) * radiusY * 0.65f;
        final float lobeAX = cos * aForward - sin * aSide, lobeAZ = sin * aForward + cos * aSide;

        h = mix64(h + 0x9E3779B97F4A7C15L); final float bAngle = angle + 1.55f + (unit(h) - 0.5f) * 0.75f;
        final float cosB = (float) Math.cos(bAngle), sinB = (float) Math.sin(bAngle);
        h = mix64(h + 0x9E3779B97F4A7C15L); final float bDistance = radiusZ * (0.34f + unit(h) * 0.38f);
        h = mix64(h + 0x9E3779B97F4A7C15L); final float lobeBY = (unit(h) - 0.35f) * radiusY * 0.90f;
        final float lobeBX = cosB * bDistance, lobeBZ = sinB * bDistance;

        h = mix64(h + 0x9E3779B97F4A7C15L); final float lobeAVerticalScale = 0.52f + unit(h) * 0.34f;
        h = mix64(h + 0x9E3779B97F4A7C15L); final float lobeBVerticalScale = 0.46f + unit(h) * 0.38f;
        h = mix64(h + 0x9E3779B97F4A7C15L); final float biomeRoll = unit(h);
        final float lushLimit = runtime.megaLushChance / 100.0f;
        final float dripstoneLimit = Math.min(1.0f, lushLimit + runtime.megaDripstoneChance / 100.0f);
        final int biomeType = (biomeRoll < lushLimit) ? MEGA_BIOME_LUSH
                : ((biomeRoll < dripstoneLimit) ? MEGA_BIOME_DRIPSTONE : MEGA_BIOME_NORMAL);
        return new MegaRegion(active && (available >= 48.0f), minimumIntensity, biomeType,
                centreX, centreY, centreZ, radiusX, radiusY, radiusZ, cos, sin,
                lobeAX, lobeAY, lobeAZ, lobeAVerticalScale,
                lobeBX, lobeBY, lobeBZ, lobeBVerticalScale, cosB, sinB);
    }

    private static float unit(long h) { return (float) ((h >>> 11) * 0x1.0p-53); }

    private float boundaryWeight(int x, int y, int z, float terrainDistance, int bottomDistance, float openingWeight) {
        final float bottomWarp = sample(boundaryNoise, x, y, z, 83.0f, 57.0f) * runtime.boundaryWarp;
        final float effectiveSurfaceClearance = runtime.surfaceClearance * (1.0f - openingWeight);
        final float bottom = smoothstep(runtime.bottomClearance - runtime.boundaryFade,
                runtime.bottomClearance + runtime.boundaryFade, bottomDistance + bottomWarp);
        final float topBase = smoothstep(effectiveSurfaceClearance - runtime.boundaryFade,
                effectiveSurfaceClearance + runtime.boundaryFade, terrainDistance - bottomWarp);
        final float top = topBase + (1.0f - topBase) * openingWeight;
        return bottom * top;
    }

    private void warpedCoordinates(double x, double y, double z, double[] target) {
        if (runtime.warpStrength > 0.0f) {
            x += sample(warpX, x, y, z, runtime.warpScale, runtime.warpScale) * runtime.warpStrength;
            y += sample(warpY, x + 1019, y - 503, z + 211, runtime.warpScale, runtime.warpScale) * runtime.warpStrength;
            z += sample(warpZ, x - 733, y + 389, z - 997, runtime.warpScale, runtime.warpScale) * runtime.warpStrength;
        }
        target[0] = x; target[1] = y; target[2] = z;
    }

    private void prepareCoordinates(double x, double y, double z, double[] target) {
        if (!runtime.domainRotation) { target[0] = x; target[1] = z; target[2] = y; return; }
        final double xz = x + z;
        final double s2 = xz * -0.211324865405187;
        final double yy = y * 0.577350269189626;
        target[0] = x + s2 + yy;
        target[1] = z + s2 + yy;
        target[2] = xz * -0.577350269189626 + yy;
    }

    private static float samplePrepared(PerlinNoise noise, double[] p, float h, float v) {
        return noise.getPerlinNoise(p[0] / h, p[1] / h, p[2] / v);
    }

    private static float fbmPrepared(PerlinNoise a, PerlinNoise b, double[] p, float h, float v) {
        return samplePrepared(a, p, h, v) * 0.68f + samplePrepared(b, p, h * 0.51f, v * 0.51f) * 0.32f;
    }

    private static float fbmWorld(PerlinNoise a, PerlinNoise b, double x, double y, double z, float h, float v) {
        return a.getPerlinNoise(x / h, z / h, y / v) * 0.72f
                + b.getPerlinNoise(x / (h * 0.48f), z / (h * 0.48f), y / (v * 0.52f)) * 0.28f;
    }

    private float sample(PerlinNoise noise, double x, double y, double z, float horizontalScale, float verticalScale) {
        if (! runtime.domainRotation)
            return noise.getPerlinNoise(x / horizontalScale, z / horizontalScale, y / verticalScale);
        final double xz = x + z;
        final double s2 = xz * -0.211324865405187;
        final double yy = y * 0.577350269189626;
        final double xr = x + s2 + yy, zr = z + s2 + yy;
        final double yr = xz * -0.577350269189626 + yy;
        return noise.getPerlinNoise(xr / horizontalScale, zr / horizontalScale, yr / verticalScale);
    }

    private static long stableHash(String value) {
        long hash = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0xBF58476D1CE4E5B9L;
            hash ^= hash >>> 27;
        }
        return hash ^ (hash >>> 31);
    }

    private static double calibrateThreshold(NoiseHeightMap noise, double rarity, long seed) {
        if (rarity <= 0.0) return Double.POSITIVE_INFINITY;
        if (rarity >= 1.0) return Double.NEGATIVE_INFINITY;
        final double[] samples = new double[8192];
        final Random random = new Random(seed);
        for (int i = 0; i < samples.length; i++) {
            samples[i] = noise.getValue(random.nextInt(1000000) - 500000,
                    random.nextInt(1000000) - 500000);
        }
        Arrays.sort(samples);
        final int index = (int) Math.floor((1.0 - rarity) * samples.length);
        return samples[Math.max(0, Math.min(samples.length - 1, index))];
    }

    private int fluidAt(int surfaceWaterLevel, int x, int z, int minY, int maxY) {
        if (caveSettings.getWaterLevel() >= minHeight)
            return packFluid(caveSettings.getWaterLevel(), caveSettings.isFloodWithLava());
        if (caveSettings.isFloodWithLava()) return packFluid(minY + caveSettings.getLavaZoneHeight(), true);
        if (runtime.experimental) {
            if (caveSettings.getLavaFrequency() > 0) {
                final float lava = lavaNoise.getPerlinNoise(x / (float) caveSettings.getLavaScale(),
                        z / (float) caveSettings.getLavaScale(), 0.0f);
                final float lavaThreshold = 0.50f - caveSettings.getLavaFrequency() / 100.0f;
                if (lava >= lavaThreshold) {
                    return packFluid(Math.min(maxY - 3, minY + caveSettings.getLavaZoneHeight()), true);
                }
            }
        } else {
            final long hash = mix64(seededFor ^ ((long) Math.floorDiv(x, caveSettings.getLavaScale()) * 0x9E3779B97F4A7C15L)
                    ^ ((long) Math.floorDiv(z, caveSettings.getLavaScale()) * 0xC2B2AE3D27D4EB4FL));
            if ((caveSettings.getLavaFrequency() > 0)
                    && (Math.floorMod(hash, 100L) < caveSettings.getLavaFrequency())) {
                return packFluid(Math.min(maxY - 3, minY + caveSettings.getLavaZoneHeight()), true);
            }
        }
        if (caveSettings.getWaterFrequency() <= 0) return packFluid(minHeight - 1, false);
        final float wet = waterNoise.getPerlinNoise(x / (float) caveSettings.getWaterScale(), z / (float) caveSettings.getWaterScale(), 0.0f);
        final float threshold = 0.50f - caveSettings.getWaterFrequency() / 100.0f;
        return (wet >= threshold)
                ? packFluid(Math.max(minY + 5, Math.min(maxY - 3, Math.min(surfaceWaterLevel, 54))), false)
                : packFluid(minHeight - 1, false);
    }

    private static int packFluid(int level, boolean lava) { return (level << 1) | (lava ? 1 : 0); }

    private void seedNoises(long seed) {
        if (seededFor == seed) return;
        megaRegionCache.clear();
        megaNeighbourhoodCache.clear();
        seededFor = seed;
        final PerlinNoise[] noises = { cheeseA, cheeseB, grandA, grandB, spaghettiA, spaghettiB,
                backboneA, backboneB, noodleA, noodleB, detailNoise, warpX, warpY, warpZ,
                surfaceSelector, boundaryNoise, waterNoise, lavaNoise };
        for (int i = 0; i < noises.length; i++) noises[i].setSeed(seed + SEED_OFFSETS[i]);
        final long pluginSeed = seed ^ 0xCA7E6E7AL;
        pluginCheeseA = new CaveSystemSimplexNoise3D(pluginSeed);
        pluginCheeseB = new CaveSystemSimplexNoise3D(pluginSeed ^ 0xA5A5A5A5L);
        pluginPillarField = new CaveSystemWorleyNoise3D(pluginSeed ^ 0x50EDCA7L);
        pluginPillarDetail = new CaveSystemSimplexNoise3D(pluginSeed ^ 0xB1770B5EL);
    }

    private static float smoothMax(float a, float b, float k) {
        if (a <= NEGATIVE) return b; if (b <= NEGATIVE) return a; if (k <= 0.0001f) return Math.max(a, b);
        final float h = clamp(0.5f + 0.5f * (a - b) / k, 0.0f, 1.0f);
        return b + (a - b) * h + k * h * (1.0f - h);
    }
    private static float smoothstep(float e0, float e1, float v) { final float t=clamp((v-e0)/Math.max(0.001f,e1-e0),0,1); return t*t*(3-2*t); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static long mix64(long h) { h^=h>>>30; h*=0xBF58476D1CE4E5B9L; h^=h>>>27; h*=0x94D049BB133111EBL; return h^(h>>>31); }

    private final PerlinNoise cheeseA=new PerlinNoise(0), cheeseB=new PerlinNoise(0), grandA=new PerlinNoise(0), grandB=new PerlinNoise(0);
    private final PerlinNoise spaghettiA=new PerlinNoise(0), spaghettiB=new PerlinNoise(0), backboneA=new PerlinNoise(0), backboneB=new PerlinNoise(0);
    private final PerlinNoise noodleA=new PerlinNoise(0), noodleB=new PerlinNoise(0), detailNoise=new PerlinNoise(0);
    private final PerlinNoise warpX=new PerlinNoise(0), warpY=new PerlinNoise(0), warpZ=new PerlinNoise(0);
    private final PerlinNoise surfaceSelector=new PerlinNoise(0), boundaryNoise=new PerlinNoise(0), waterNoise=new PerlinNoise(0), lavaNoise=new PerlinNoise(0);
    private CaveSystemSimplexNoise3D pluginCheeseA, pluginCheeseB, pluginPillarDetail;
    private CaveSystemWorleyNoise3D pluginPillarField;
    private final CaveSystemSettings caveSettings;
    private final BitSet excavatedBlocks = new BitSet();
    private final BitSet lushMegaBlocks = new BitSet(), dripstoneMegaBlocks = new BitSet(), lushCheeseBlocks = new BitSet();
    private final NoiseHeightMap pluginLushCheeseNoise;
    private final double pluginLushCheeseThreshold;
    private final Map<Long, MegaRegion> megaRegionCache = new ConcurrentHashMap<>();
    private final Map<Long, MegaRegion[]> megaNeighbourhoodCache = new ConcurrentHashMap<>();
    private final RuntimeConfig runtime;
    private long seededFor=Long.MIN_VALUE;
    private static final long[] SEED_OFFSETS={1009,1013,1019,1021,1031,1033,1039,1049,1051,1061,1063,1069,1087,1091,1093,1097,1103,1109};
    private static final float NEGATIVE=-1000000.0f;
    private static final int MEGA_CELL_SIZE=256;
    private static final int N_CHEESE=0,N_DETAIL=1,N_GRAND=2,N_SPAGHETTI_A=3,N_SPAGHETTI_B=4,
            N_BACKBONE_A=5,N_BACKBONE_B=6,N_NOODLE_A=7,N_NOODLE_B=8,NOISE_VALUE_COUNT=9;
    private static final int CAVE_NONE=0,CAVE_TUNNEL=1,CAVE_CHEESE=2,CAVE_GRAND=3,CAVE_BACKBONE=4;
    private static final int MEGA_BIOME_NORMAL=0,MEGA_BIOME_LUSH=1,MEGA_BIOME_DRIPSTONE=2;

    private static final class RuntimeConfig {
        RuntimeConfig(CaveSystemSettings s) {
            experimental=s.getGenerationVersion() == CaveSystemSettings.GENERATION_L2_0_1_EXPERIMENTAL;
            domainRotation=s.isDomainRotation();warpStrength=s.getWarpStrength();warpScale=s.getWarpScale();
            globalDensityOffset=(s.getOverallDensity()-100)*0.0012f;unionSmoothness=s.getUnionSmoothness()/100.0f*0.10f;
            final float spacing=s.getCaveSpacing()/100.0f;
            cheeseEnabled=s.getCheeseFrequency()>0;grandEnabled=s.getGrandFrequency()>0;
            spaghettiEnabled=s.getSpaghettiFrequency()>0;backboneEnabled=s.getBackboneFrequency()>0;noodleEnabled=s.getNoodleFrequency()>0;
            cheeseH=118.0f*s.getCheeseHorizontalScale()/100.0f*spacing;cheeseV=74.0f*s.getCheeseVerticalScale()/100.0f;
            pluginCheeseSize=Math.max(0.25f,2.0f*s.getCheeseHorizontalScale()/100.0f*spacing);
            pluginCheeseVerticalMultiplier=100.0f/Math.max(1,s.getCheeseVerticalScale());
            pluginCheeseAmount=clamp(0.40f+(s.getCheeseFrequency()-120)*0.0025f
                    +(s.getCheeseSize()-115)*0.0015f+(s.getOverallDensity()-85)*0.0020f,0.0f,1.0f);
            pluginCheesePersistence=clamp(0.50f+(s.getDetailStrength()-45)*0.0020f,0.20f,0.80f);
            grandH=220.0f*s.getGrandHorizontalScale()/100.0f*spacing;grandV=125.0f*s.getGrandVerticalScale()/100.0f;
            spaghettiH=65.0f*spacing;backboneH=92.0f*spacing;noodleH=43.0f*spacing;
            detailFactor=s.getDetailStrength()/100.0f*0.10f;
            cheeseThreshold=0.235f+(100-s.getCheeseSize())*0.0012f+(100-s.getCheeseFrequency())*0.0012f;
            grandThreshold=0.255f+(100-s.getGrandSize())*0.0012f+(100-s.getGrandFrequency())*0.0015f;
            megaFrequency=s.getMegaFrequency();megaHorizontalScale=s.getMegaHorizontalScale()/100.0f;
            megaVerticalScale=s.getMegaVerticalScale()/100.0f;megaTallChance=s.getMegaTallChance();
            megaLushChance=s.getMegaLushChance();megaDripstoneChance=s.getMegaDripstoneChance();
            spaghettiBase=0.090f*s.getSpaghettiWidth()/100.0f+(s.getSpaghettiFrequency()-100)*0.00025f;
            backboneBase=0.071f*s.getBackboneWidth()/100.0f+(s.getBackboneFrequency()-100)*0.00022f;
            noodleBase=0.052f*s.getNoodleWidth()/100.0f+(s.getNoodleFrequency()-100)*0.00018f;
            openCheese=s.isOpenCheese();openGrand=s.isOpenGrand();openSpaghetti=s.isOpenSpaghetti();
            openBackbone=s.isOpenBackbone();openNoodles=s.isOpenNoodles();
            needsClosedBoundary=!openCheese||!openGrand||!openSpaghetti||!openBackbone||!openNoodles;
            openingFrequency=s.getSurfaceOpeningFrequency();openingScale=s.getSurfaceOpeningScale();
            openingThreshold=0.50f-openingFrequency/100.0f;
            openingBand=(s.getSurfaceOpeningSoftness()==40)?0.08f:0.02f+s.getSurfaceOpeningSoftness()/100.0f*0.15f;
            openingStrength=s.getSurfaceOpeningStrength()/100.0f;openingDepth=s.getSurfaceOpeningDepth();
            boundaryFade=Math.max(1.0f,s.getBoundaryFade());boundaryWarp=s.getBoundaryWarp();
            bottomClearance=s.getBottomClearance();surfaceClearance=s.getSurfaceClearance();
        }
        final boolean experimental,domainRotation,cheeseEnabled,grandEnabled,spaghettiEnabled,backboneEnabled,noodleEnabled;
        final boolean openCheese,openGrand,openSpaghetti,openBackbone,openNoodles,needsClosedBoundary;
        final int openingFrequency,megaFrequency,megaTallChance,megaLushChance,megaDripstoneChance;
        final float megaHorizontalScale,megaVerticalScale,warpStrength,warpScale,globalDensityOffset,unionSmoothness;
        final float cheeseH,cheeseV,pluginCheeseSize,pluginCheeseVerticalMultiplier,pluginCheeseAmount,pluginCheesePersistence;
        final float grandH,grandV,spaghettiH,backboneH,noodleH,detailFactor;
        final float cheeseThreshold,grandThreshold,spaghettiBase,backboneBase,noodleBase;
        final float openingScale,openingThreshold,openingBand,openingStrength,openingDepth;
        final float boundaryFade,boundaryWarp,bottomClearance,surfaceClearance;
    }

    private static final class MegaRegion {
        MegaRegion(boolean active, float minimumIntensity, int biomeType,
                   double centreX, double centreY, double centreZ,
                   float radiusX, float radiusY, float radiusZ, float cos, float sin,
                   float lobeAX, float lobeAY, float lobeAZ, float lobeAVerticalScale,
                   float lobeBX, float lobeBY, float lobeBZ, float lobeBVerticalScale,
                   float cosB, float sinB) {
            this.active=active;this.minimumIntensity=minimumIntensity;this.biomeType=biomeType;
            this.centreX=centreX;this.centreY=centreY;this.centreZ=centreZ;
            this.radiusX=radiusX;this.radiusY=radiusY;this.radiusZ=radiusZ;this.cos=cos;this.sin=sin;
            this.lobeAX=lobeAX;this.lobeAY=lobeAY;this.lobeAZ=lobeAZ;this.lobeAVerticalScale=lobeAVerticalScale;
            this.lobeBX=lobeBX;this.lobeBY=lobeBY;this.lobeBZ=lobeBZ;this.lobeBVerticalScale=lobeBVerticalScale;
            this.cosB=cosB;this.sinB=sinB;
            final float lobeReach=Math.max((float)Math.hypot(lobeAX,lobeAZ),(float)Math.hypot(lobeBX,lobeBZ));
            horizontalReachSquared=(24.0f+Math.max(radiusX,radiusZ)*1.45f+lobeReach)
                    *(24.0f+Math.max(radiusX,radiusZ)*1.45f+lobeReach);
            verticalReach=18.0f+radiusY*1.45f+Math.max(Math.abs(lobeAY),Math.abs(lobeBY));
        }
        final boolean active; final float minimumIntensity; final int biomeType;
        final double centreX,centreY,centreZ; final float radiusX,radiusY,radiusZ,cos,sin;
        final float horizontalReachSquared,verticalReach;
        final float lobeAX,lobeAY,lobeAZ,lobeAVerticalScale,lobeBX,lobeBY,lobeBZ,lobeBVerticalScale,cosB,sinB;
    }

    private static final class DensityColumn {
        DensityColumn(int surfaceY, int minY, float intensity, float openingWeight,
                      float gradientX, float gradientZ, int[] haloHeights, float[] haloDistances,
                      MegaRegion[] megaRegions) {
            this.surfaceY=surfaceY; this.minY=minY; this.intensity=intensity; this.openingWeight=openingWeight;
            this.gradientX=gradientX; this.gradientZ=gradientZ; this.haloHeights=haloHeights; this.haloDistances=haloDistances;
            this.megaRegions=megaRegions;
            normalLength=(float)Math.sqrt(1.0f + gradientX*gradientX + gradientZ*gradientZ);
        }
        float terrainDistance(int y) {
            float distance=(surfaceY-y)/normalLength;
            for(int i=0;i<haloHeights.length;i++) {
                distance=Math.min(distance, haloHeights[i]-y + haloDistances[i]*0.82f);
            }
            return distance;
        }
        final int surfaceY,minY; final float intensity,openingWeight,gradientX,gradientZ,normalLength;
        final int[] haloHeights; final float[] haloDistances; final MegaRegion[] megaRegions;
        int frameAY=Integer.MIN_VALUE,frameBY=Integer.MIN_VALUE;
        final double[] warped=new double[3],prepared=new double[3];
        final float[] frameA=new float[NOISE_VALUE_COUNT],frameB=new float[NOISE_VALUE_COUNT],interpolated=new float[NOISE_VALUE_COUNT];
        int lastMegaBiome=MEGA_BIOME_NORMAL;
    }
    public static final class CaveSystemSettings implements CaveSettings {
        public static final int GENERATION_L2_0_0 = 0;
        public static final int GENERATION_L2_0_1_EXPERIMENTAL = 1;
        public CaveSystemSettings() { syncDecorationSettings(); }
        public int getGenerationVersion() { return (generationVersion == GENERATION_L2_0_1_EXPERIMENTAL) ? GENERATION_L2_0_1_EXPERIMENTAL : GENERATION_L2_0_0; }
        public void setGenerationVersion(int value) {
            generationVersion = (value == GENERATION_L2_0_1_EXPERIMENTAL)
                    ? GENERATION_L2_0_1_EXPERIMENTAL : GENERATION_L2_0_0;
        }
        private void syncDecorationSettings() {
            decorationSettings = new CaveDecorationSettings(false, true, lushCaves, dripstoneCaves);
            decorationSettings.setLushThresholdOffset(lushRarity);
            decorationSettings.setDripstoneThresholdOffset(dripstoneRarity);
            decorationSettings.setMixedPatchChance(mixedPatchChance);
            decorationSettings.setBiomePatchScale(CaveDecorationSettings.Decoration.LUSH_CAVE_PATCHES, lushRegionScale / 10.0f);
            decorationSettings.setBiomePatchScale(CaveDecorationSettings.Decoration.DRIPSTONE_CAVE_PATCHES, dripstoneRegionScale / 10.0f);
            decorationSettings.setEnhancedLushFeatures(enhancedLushFeatures);
            decorationSettings.setLushPoolFrequency(lushPoolFrequency);
            decorationSettings.setLushPoolCellSize(lushPoolSpacing);
            decorationSettings.setLushPoolMinRadius(lushPoolMinRadius);
            decorationSettings.setLushPoolMaxRadius(lushPoolMaxRadius);
            decorationSettings.setLushPoolDryChance(lushPoolDryChance);
            decorationSettings.setEnhancedDripstoneFeatures(enhancedDripstoneFeatures);
            decorationSettings.setDripstonePatchCoverage(dripstonePatchCoverage);
            decorationSettings.setSmallDripstoneFrequency(smallDripstoneFrequency);
            decorationSettings.setLargeDripstoneFrequency(largeDripstoneFrequency);
            decorationSettings.setLargeDripstoneCellSize(largeDripstoneSpacing);
            decorationSettings.setLargeDripstoneMaxRadius(largeDripstoneMaxRadius);
            decorationSettings.setLargeDripstoneSearchHeight(largeDripstoneSearchHeight);
        }
        public boolean isLushCaves() { return lushCaves; }
        public void setLushCaves(boolean value) { lushCaves=value; syncDecorationSettings(); }
        public boolean isDripstoneCaves() { return dripstoneCaves; }
        public void setDripstoneCaves(boolean value) { dripstoneCaves=value; syncDecorationSettings(); }
        public int getLushRarity(){return lushRarity;} public void setLushRarity(int v){lushRarity=clampI(v,0,250);syncDecorationSettings();}
        public int getDripstoneRarity(){return dripstoneRarity;} public void setDripstoneRarity(int v){dripstoneRarity=clampI(v,0,250);syncDecorationSettings();}
        public int getLushRegionScale(){return lushRegionScale;} public void setLushRegionScale(int v){lushRegionScale=clampI(v,5,200);syncDecorationSettings();}
        public int getDripstoneRegionScale(){return dripstoneRegionScale;} public void setDripstoneRegionScale(int v){dripstoneRegionScale=clampI(v,5,200);syncDecorationSettings();}
        public int getMixedPatchChance(){return mixedPatchChance;} public void setMixedPatchChance(int v){mixedPatchChance=clampI(v,0,100);syncDecorationSettings();}
        public boolean isEnhancedLushFeatures(){return enhancedLushFeatures;} public void setEnhancedLushFeatures(boolean v){enhancedLushFeatures=v;syncDecorationSettings();}
        public int getLushPoolFrequency(){return lushPoolFrequency;} public void setLushPoolFrequency(int v){lushPoolFrequency=clampI(v,0,100);syncDecorationSettings();}
        public int getLushPoolSpacing(){return lushPoolSpacing;} public void setLushPoolSpacing(int v){lushPoolSpacing=clampI(v,12,48);syncDecorationSettings();}
        public int getLushPoolMinRadius(){return lushPoolMinRadius;} public void setLushPoolMinRadius(int v){lushPoolMinRadius=clampI(v,2,12);if(lushPoolMaxRadius<lushPoolMinRadius)lushPoolMaxRadius=lushPoolMinRadius;syncDecorationSettings();}
        public int getLushPoolMaxRadius(){return lushPoolMaxRadius;} public void setLushPoolMaxRadius(int v){lushPoolMaxRadius=clampI(v,lushPoolMinRadius,16);syncDecorationSettings();}
        public int getLushPoolDryChance(){return lushPoolDryChance;} public void setLushPoolDryChance(int v){lushPoolDryChance=clampI(v,0,100);syncDecorationSettings();}
        public boolean isEnhancedDripstoneFeatures(){return enhancedDripstoneFeatures;} public void setEnhancedDripstoneFeatures(boolean v){enhancedDripstoneFeatures=v;syncDecorationSettings();}
        public int getDripstonePatchCoverage(){return dripstonePatchCoverage;} public void setDripstonePatchCoverage(int v){dripstonePatchCoverage=clampI(v,0,100);syncDecorationSettings();}
        public int getSmallDripstoneFrequency(){return smallDripstoneFrequency;} public void setSmallDripstoneFrequency(int v){smallDripstoneFrequency=clampI(v,0,100);syncDecorationSettings();}
        public int getLargeDripstoneFrequency(){return largeDripstoneFrequency;} public void setLargeDripstoneFrequency(int v){largeDripstoneFrequency=clampI(v,0,100);syncDecorationSettings();}
        public int getLargeDripstoneSpacing(){return largeDripstoneSpacing;} public void setLargeDripstoneSpacing(int v){largeDripstoneSpacing=clampI(v,16,64);syncDecorationSettings();}
        public int getLargeDripstoneMaxRadius(){return largeDripstoneMaxRadius;} public void setLargeDripstoneMaxRadius(int v){largeDripstoneMaxRadius=clampI(v,4,32);syncDecorationSettings();}
        public int getLargeDripstoneSearchHeight(){return largeDripstoneSearchHeight;} public void setLargeDripstoneSearchHeight(int v){largeDripstoneSearchHeight=clampI(v,24,192);syncDecorationSettings();}
        @Override public boolean isApplyEverywhere(){return minimumLevel>0;}
        @Override public CaveSystem getLayer(){return CaveSystem.INSTANCE;}
        @Override public CaveDecorationSettings getCaveDecorationSettings(){return decorationSettings;}
        public void setCaveDecorationSettings(CaveDecorationSettings v){decorationSettings=v;}
        public boolean isSurfaceBreaking(){return surfaceBreaking;} public void setSurfaceBreaking(boolean v){surfaceBreaking=v;}
        public boolean isLeaveWater(){return leaveWater;} public void setLeaveWater(boolean v){leaveWater=v;}
        public boolean isFloodWithLava(){return floodWithLava;} public void setFloodWithLava(boolean v){floodWithLava=v;}
        public int getWaterLevel(){return waterLevel;} public void setWaterLevel(int v){waterLevel=v;}
        public int getMinimumLevel(){return minimumLevel;} public void setMinimumLevel(int v){minimumLevel=v;}
        public int getMinimumY(){return minimumY;} public void setMinimumY(int v){minimumY=v;}
        public boolean isDomainRotation(){return domainRotation;} public void setDomainRotation(boolean v){domainRotation=v;}
        public int getWarpStrength(){return warpStrength;} public void setWarpStrength(int v){warpStrength=clampI(v,0,64);}
        public int getWarpScale(){return warpScale;} public void setWarpScale(int v){warpScale=clampI(v,32,256);}
        public int getDetailStrength(){return detailStrength;} public void setDetailStrength(int v){detailStrength=clampI(v,0,200);}
        public int getUnionSmoothness(){return unionSmoothness;} public void setUnionSmoothness(int v){unionSmoothness=clampI(v,0,100);}
        public int getOverallDensity(){return overallDensity;} public void setOverallDensity(int v){overallDensity=clampI(v,0,200);}
        public int getCaveSpacing(){return caveSpacing;} public void setCaveSpacing(int v){caveSpacing=clampI(v,50,300);}
        public int getCheeseFrequency(){return cheeseFrequency;} public void setCheeseFrequency(int v){cheeseFrequency=clampI(v,0,200);}
        public int getCheeseSize(){return cheeseSize;} public void setCheeseSize(int v){cheeseSize=clampI(v,50,200);}
        public int getCheeseHorizontalScale(){return cheeseHorizontalScale;} public void setCheeseHorizontalScale(int v){cheeseHorizontalScale=clampI(v,40,250);}
        public int getCheeseVerticalScale(){return cheeseVerticalScale;} public void setCheeseVerticalScale(int v){cheeseVerticalScale=clampI(v,40,250);}
        public int getGrandFrequency(){return grandFrequency;} public void setGrandFrequency(int v){grandFrequency=clampI(v,0,200);}
        public int getGrandSize(){return grandSize;} public void setGrandSize(int v){grandSize=clampI(v,50,250);}
        public int getGrandHorizontalScale(){return grandHorizontalScale;} public void setGrandHorizontalScale(int v){grandHorizontalScale=clampI(v,50,250);}
        public int getGrandVerticalScale(){return grandVerticalScale;} public void setGrandVerticalScale(int v){grandVerticalScale=clampI(v,50,250);}
        public int getMegaFrequency(){return megaFrequency;} public void setMegaFrequency(int v){megaFrequency=clampI(v,0,100);}
        public int getMegaHorizontalScale(){return megaHorizontalScale;} public void setMegaHorizontalScale(int v){megaHorizontalScale=clampI(v,40,200);}
        public int getMegaVerticalScale(){return megaVerticalScale;} public void setMegaVerticalScale(int v){megaVerticalScale=clampI(v,40,200);}
        public int getMegaTallChance(){return megaTallChance;} public void setMegaTallChance(int v){megaTallChance=clampI(v,0,100);}
        public int getMegaLushChance(){return megaLushChance;} public void setMegaLushChance(int v){megaLushChance=clampI(v,0,100);syncDecorationSettings();}
        public int getMegaDripstoneChance(){return megaDripstoneChance;} public void setMegaDripstoneChance(int v){megaDripstoneChance=clampI(v,0,100);syncDecorationSettings();}
        public int getSpaghettiFrequency(){return spaghettiFrequency;} public void setSpaghettiFrequency(int v){spaghettiFrequency=clampI(v,0,200);}
        public int getSpaghettiWidth(){return spaghettiWidth;} public void setSpaghettiWidth(int v){spaghettiWidth=clampI(v,40,220);}
        public int getBackboneFrequency(){return backboneFrequency;} public void setBackboneFrequency(int v){backboneFrequency=clampI(v,0,200);}
        public int getBackboneWidth(){return backboneWidth;} public void setBackboneWidth(int v){backboneWidth=clampI(v,40,220);}
        public int getNoodleFrequency(){return noodleFrequency;} public void setNoodleFrequency(int v){noodleFrequency=clampI(v,0,200);}
        public int getNoodleWidth(){return noodleWidth;} public void setNoodleWidth(int v){noodleWidth=clampI(v,40,220);}
        public int getBottomClearance(){return bottomClearance;} public void setBottomClearance(int v){bottomClearance=clampI(v,0,128);}
        public int getSurfaceClearance(){return surfaceClearance;} public void setSurfaceClearance(int v){surfaceClearance=clampI(v,0,128);}
        public int getBoundaryFade(){return boundaryFade;} public void setBoundaryFade(int v){boundaryFade=clampI(v,0,64);}
        public int getBoundaryWarp(){return boundaryWarp;} public void setBoundaryWarp(int v){boundaryWarp=clampI(v,0,32);}
        public int getSurfaceOpeningFrequency(){return surfaceOpeningFrequency;} public void setSurfaceOpeningFrequency(int v){surfaceOpeningFrequency=clampI(v,0,100);}
        public int getSurfaceOpeningStrength(){return surfaceOpeningStrength;} public void setSurfaceOpeningStrength(int v){surfaceOpeningStrength=clampI(v,0,200);}
        public int getSurfaceOpeningDepth(){return surfaceOpeningDepth;} public void setSurfaceOpeningDepth(int v){surfaceOpeningDepth=clampI(v,4,64);}
        public int getSurfaceOpeningScale(){return surfaceOpeningScale;} public void setSurfaceOpeningScale(int v){surfaceOpeningScale=clampI(v,64,400);}
        public int getSurfaceOpeningSoftness(){return surfaceOpeningSoftness;} public void setSurfaceOpeningSoftness(int v){surfaceOpeningSoftness=clampI(v,0,100);}
        public boolean isOpenCheese(){return openCheese;} public void setOpenCheese(boolean v){openCheese=v;}
        public boolean isOpenGrand(){return openGrand;} public void setOpenGrand(boolean v){openGrand=v;}
        public boolean isOpenSpaghetti(){return openSpaghetti;} public void setOpenSpaghetti(boolean v){openSpaghetti=v;}
        public boolean isOpenBackbone(){return openBackbone;} public void setOpenBackbone(boolean v){openBackbone=v;}
        public boolean isOpenNoodles(){return openNoodles;} public void setOpenNoodles(boolean v){openNoodles=v;}
        public int getWaterFrequency(){return waterFrequency;} public void setWaterFrequency(int v){waterFrequency=clampI(v,0,100);}
        public int getWaterScale(){return waterScale;} public void setWaterScale(int v){waterScale=clampI(v,64,400);}
        public boolean isWaterInChambers(){return waterInChambers;} public void setWaterInChambers(boolean v){waterInChambers=v;}
        public int getLavaFrequency(){return lavaFrequency;} public void setLavaFrequency(int v){lavaFrequency=clampI(v,0,100);}
        public int getLavaScale(){return lavaScale;} public void setLavaScale(int v){lavaScale=clampI(v,64,400);}
        public int getLavaZoneHeight(){return lavaZoneHeight;} public void setLavaZoneHeight(int v){lavaZoneHeight=clampI(v,5,64);}
        public boolean isLavaInBackbone(){return lavaInBackbone;} public void setLavaInBackbone(boolean v){lavaInBackbone=v;}
        public boolean isLavaInChambers(){return lavaInChambers;} public void setLavaInChambers(boolean v){lavaInChambers=v;}
        @Override public CaveSystemSettings clone(){try{CaveSystemSettings c=(CaveSystemSettings)super.clone();if(decorationSettings!=null)c.decorationSettings=decorationSettings.clone();return c;}catch(CloneNotSupportedException e){throw new RuntimeException(e);}}
        @Override public boolean equals(Object obj){if(!(obj instanceof CaveSystemSettings))return false;CaveSystemSettings o=(CaveSystemSettings)obj;return hashCode()==o.hashCode();}
        @Override public int hashCode(){return Objects.hash(generationVersion,lushCaves,dripstoneCaves,lushRarity,dripstoneRarity,lushRegionScale,dripstoneRegionScale,mixedPatchChance,enhancedLushFeatures,lushPoolFrequency,lushPoolSpacing,lushPoolMinRadius,lushPoolMaxRadius,lushPoolDryChance,enhancedDripstoneFeatures,dripstonePatchCoverage,smallDripstoneFrequency,largeDripstoneFrequency,largeDripstoneSpacing,largeDripstoneMaxRadius,largeDripstoneSearchHeight,surfaceBreaking,leaveWater,floodWithLava,waterLevel,minimumLevel,minimumY,domainRotation,warpStrength,warpScale,detailStrength,unionSmoothness,overallDensity,caveSpacing,cheeseFrequency,cheeseSize,cheeseHorizontalScale,cheeseVerticalScale,grandFrequency,grandSize,grandHorizontalScale,grandVerticalScale,megaFrequency,megaHorizontalScale,megaVerticalScale,megaTallChance,megaLushChance,megaDripstoneChance,spaghettiFrequency,spaghettiWidth,backboneFrequency,backboneWidth,noodleFrequency,noodleWidth,bottomClearance,surfaceClearance,boundaryFade,boundaryWarp,surfaceOpeningFrequency,surfaceOpeningStrength,surfaceOpeningDepth,surfaceOpeningScale,surfaceOpeningSoftness,openCheese,openGrand,openSpaghetti,openBackbone,openNoodles,waterFrequency,waterScale,waterInChambers,lavaFrequency,lavaScale,lavaZoneHeight,lavaInBackbone,lavaInChambers,decorationSettings);}
        private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (settingsVersion < 2) {
                surfaceOpeningFrequency = Math.max(surfaceOpeningFrequency, 25);
                surfaceOpeningStrength = 100;
                surfaceOpeningDepth = 32;
                settingsVersion = 2;
            }
            if (settingsVersion < 3) {
                overallDensity = 100;
                caveSpacing = 100;
                surfaceOpeningScale = 165;
                surfaceOpeningSoftness = 40;
                openCheese = true;
                openGrand = true;
                openSpaghetti = true;
                openBackbone = true;
                openNoodles = true;
                settingsVersion = 3;
            }
            if (settingsVersion < 4) {
                warpStrength = 21;
                warpScale = 40;
                detailStrength = 45;
                unionSmoothness = 7;
                overallDensity = 85;
                caveSpacing = 100;
                cheeseFrequency = 120;
                cheeseSize = 115;
                cheeseHorizontalScale = 100;
                cheeseVerticalScale = 100;
                grandFrequency = 85;
                grandSize = 175;
                grandHorizontalScale = 125;
                grandVerticalScale = 105;
                spaghettiFrequency = 105;
                spaghettiWidth = 100;
                backboneFrequency = 145;
                backboneWidth = 95;
                noodleFrequency = 75;
                noodleWidth = 70;
                waterFrequency = 0;
                waterScale = 180;
                waterInChambers = false;
                lavaFrequency = 60;
                lavaScale = 180;
                lavaZoneHeight = 28;
                lavaInBackbone = true;
                lavaInChambers = false;
                settingsVersion = 4;
            }
            if (settingsVersion < 5) {
                surfaceOpeningFrequency = 35;
                surfaceOpeningStrength = 100;
                surfaceOpeningDepth = 48;
                settingsVersion = 5;
            }
            if (settingsVersion < 6) {
                surfaceOpeningFrequency = 70;
                surfaceOpeningStrength = 100;
                surfaceOpeningDepth = 48;
                settingsVersion = 6;
            }
            if (settingsVersion < 7) {
                lushCaves = true;
                dripstoneCaves = true;
                settingsVersion = 7;
            }
            if (settingsVersion < 8) {
                grandFrequency = 75;
                grandSize = 220;
                grandHorizontalScale = 100;
                grandVerticalScale = 155;
                waterFrequency = 18;
                waterInChambers = true;
                settingsVersion = 8;
            }
            if (settingsVersion < 9) settingsVersion = 9;
            if (settingsVersion < 10) settingsVersion = 10;
            if (settingsVersion < 11) settingsVersion = 11;
            if (settingsVersion < 12) settingsVersion = 12;
            if (settingsVersion < 13) {
                if (surfaceOpeningFrequency == 70) surfaceOpeningFrequency = 28;
                settingsVersion = 13;
            }
            if (settingsVersion < 14) settingsVersion = 14;
            if (settingsVersion < 15) {
                megaFrequency = 27; megaHorizontalScale = 100; megaVerticalScale = 100; megaTallChance = 24;
                megaLushChance = 20; megaDripstoneChance = 28;
                lushRarity = 105; dripstoneRarity = 20; lushRegionScale = 80; dripstoneRegionScale = 100;
                mixedPatchChance = 8; enhancedLushFeatures = true; lushPoolFrequency = 63; lushPoolSpacing = 20;
                lushPoolMinRadius = 4; lushPoolMaxRadius = 8; lushPoolDryChance = 13;
                enhancedDripstoneFeatures = true; dripstonePatchCoverage = 50; smallDripstoneFrequency = 25;
                largeDripstoneFrequency = 56; largeDripstoneSpacing = 32; largeDripstoneMaxRadius = 19;
                largeDripstoneSearchHeight = 96; settingsVersion = 15;
            }
            syncDecorationSettings();
        }
        private static int clampI(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
        private boolean surfaceBreaking=true,leaveWater=true,floodWithLava,domainRotation=true,waterInChambers=true,lavaInBackbone=true,lavaInChambers;
        private boolean lushCaves=true, dripstoneCaves=true, enhancedLushFeatures=true, enhancedDripstoneFeatures=true;
        private boolean openCheese=true,openGrand=true,openSpaghetti=true,openBackbone=true,openNoodles=true;
        private int waterLevel=Integer.MIN_VALUE,minimumLevel,minimumY=Integer.MIN_VALUE;
        private int warpStrength=21,warpScale=40,detailStrength=45,unionSmoothness=7,overallDensity=85,caveSpacing=100;
        private int cheeseFrequency=120,cheeseSize=115,cheeseHorizontalScale=100,cheeseVerticalScale=100;
        private int grandFrequency=75,grandSize=220,grandHorizontalScale=100,grandVerticalScale=155;
        private int megaFrequency=27,megaHorizontalScale=100,megaVerticalScale=100,megaTallChance=24,megaLushChance=20,megaDripstoneChance=28;
        private int spaghettiFrequency=105,spaghettiWidth=100,backboneFrequency=145,backboneWidth=95,noodleFrequency=75,noodleWidth=70;
        private int bottomClearance=8,surfaceClearance=14,boundaryFade=10,boundaryWarp=8;
        private int surfaceOpeningFrequency=28,surfaceOpeningStrength=100,surfaceOpeningDepth=48;
        private int surfaceOpeningScale=165,surfaceOpeningSoftness=40;
        private int waterFrequency=18,waterScale=180,lavaFrequency=60,lavaScale=180,lavaZoneHeight=28;
        private int lushRarity=105,dripstoneRarity=20,lushRegionScale=80,dripstoneRegionScale=100,mixedPatchChance=8;
        private int lushPoolFrequency=63,lushPoolSpacing=20,lushPoolMinRadius=4,lushPoolMaxRadius=8,lushPoolDryChance=13;
        private int dripstonePatchCoverage=50,smallDripstoneFrequency=25,largeDripstoneFrequency=56;
        private int largeDripstoneSpacing=32,largeDripstoneMaxRadius=19,largeDripstoneSearchHeight=96;
        private int settingsVersion=15;
        private int generationVersion=GENERATION_L2_0_1_EXPERIMENTAL;
        private CaveDecorationSettings decorationSettings;
        private static final long serialVersionUID=1L;
    }
}
