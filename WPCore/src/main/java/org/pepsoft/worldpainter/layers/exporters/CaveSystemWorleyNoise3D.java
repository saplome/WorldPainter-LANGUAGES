/*
 * WorldPainter Languages, an unofficial localization fork of WorldPainter
 * (https://github.com/saplome/WorldPainter-LANGUAGES).
 * Copyright © 2026 saplome
 * Noise algorithm adapted from WPCaveGeneratorPlugin 0.1.5 by wp_dude.
 * Licensed under the GNU General Public License, version 3.
 */
package org.pepsoft.worldpainter.layers.exporters;

import java.util.Random;

final class CaveSystemWorleyNoise3D {
    private final long seed;

    public CaveSystemWorleyNoise3D(long seed) {
        this.seed = seed;
    }

    public Cell nearest(double x, double y, double z) {
        int ix = CaveSystemWorleyNoise3D.floor(x);
        int iy = CaveSystemWorleyNoise3D.floor(y);
        int iz = CaveSystemWorleyNoise3D.floor(z);
        double f1 = Double.MAX_VALUE;
        int bestCX = ix;
        int bestCY = iy;
        int bestCZ = iz;
        long bestHash = 0L;
        double bestFX = (double)ix + 0.5;
        double bestFY = (double)iy + 0.5;
        double bestFZ = (double)iz + 0.5;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dx = -1; dx <= 1; ++dx) {
                    double fz;
                    double fy;
                    int cx = ix + dx;
                    int cy = iy + dy;
                    int cz = iz + dz;
                    long h = this.hash(cx, cy, cz);
                    double fx = (double)cx + (double)(h & 0xFFFFL) / 65536.0;
                    double d2 = (fx - x) * (fx - x) + ((fy = (double)cy + (double)(h >>> 16 & 0xFFFFL) / 65536.0) - y) * (fy - y) + ((fz = (double)cz + (double)(h >>> 32 & 0xFFFFL) / 65536.0) - z) * (fz - z);
                    if (!(d2 < f1)) continue;
                    f1 = d2;
                    bestCX = cx;
                    bestCY = cy;
                    bestCZ = cz;
                    bestHash = h;
                    bestFX = fx;
                    bestFY = fy;
                    bestFZ = fz;
                }
            }
        }
        long mixed = CaveSystemWorleyNoise3D.mix(bestHash ^ 0x243F6A8885A308D3L);
        double value = (double)(mixed >>> 11 & 0x1FFFFFFFFFFFFFL) / 9.007199254740992E15;
        return new Cell(Math.sqrt(f1), bestCX, bestCY, bestCZ, value, bestFX, bestFY, bestFZ);
    }

    public double[] distances(double x, double y, double z) {
        int ix = CaveSystemWorleyNoise3D.floor(x);
        int iy = CaveSystemWorleyNoise3D.floor(y);
        int iz = CaveSystemWorleyNoise3D.floor(z);
        double f1 = Double.MAX_VALUE;
        double f2 = Double.MAX_VALUE;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dx = -1; dx <= 1; ++dx) {
                    double fz;
                    double fy;
                    int cx = ix + dx;
                    int cy = iy + dy;
                    int cz = iz + dz;
                    long h = this.hash(cx, cy, cz);
                    double fx = (double)cx + (double)(h & 0xFFFFL) / 65536.0;
                    double d2 = (fx - x) * (fx - x) + ((fy = (double)cy + (double)(h >>> 16 & 0xFFFFL) / 65536.0) - y) * (fy - y) + ((fz = (double)cz + (double)(h >>> 32 & 0xFFFFL) / 65536.0) - z) * (fz - z);
                    if (d2 < f1) {
                        f2 = f1;
                        f1 = d2;
                        continue;
                    }
                    if (!(d2 < f2)) continue;
                    f2 = d2;
                }
            }
        }
        return new double[]{Math.sqrt(f1), Math.sqrt(f2)};
    }

    public double ridge(double x, double y, double z) {
        double[] d = this.distances(x, y, z);
        return d[1] - d[0];
    }

    private long hash(int x, int y, int z) {
        long h = this.seed;
        h ^= (long)x * -7046029254386353131L;
        h ^= (long)y * -4658895280553007687L;
        h ^= (long)z * -7723592293110705685L;
        h ^= h >>> 30;
        h *= -4658895280553007687L;
        h ^= h >>> 27;
        h *= -7723592293110705685L;
        h ^= h >>> 31;
        return h;
    }

    private static long mix(long h) {
        h ^= h >>> 30;
        h *= -4658895280553007687L;
        h ^= h >>> 27;
        h *= -7723592293110705685L;
        h ^= h >>> 31;
        return h;
    }

    private static int floor(double v) {
        int i = (int)v;
        return v < (double)i ? i - 1 : i;
    }

    private Random rng() {
        return new Random(this.seed);
    }

    public static final class Cell {
        public final double f1;
        public final int cellX;
        public final int cellY;
        public final int cellZ;
        public final double value;
        public final double fx;
        public final double fy;
        public final double fz;

        Cell(double f1, int cellX, int cellY, int cellZ, double value, double fx, double fy, double fz) {
            this.f1 = f1;
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
            this.value = value;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
        }
    }
}

