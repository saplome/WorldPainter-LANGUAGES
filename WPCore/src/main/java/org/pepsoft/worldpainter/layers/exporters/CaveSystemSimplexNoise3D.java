/*
 * WorldPainter Languages, an unofficial localization fork of WorldPainter
 * (https://github.com/saplome/WorldPainter-LANGUAGES).
 * Copyright © 2026 saplome
 * Noise algorithm adapted from WPCaveGeneratorPlugin 0.1.5 by wp_dude.
 * Licensed under the GNU General Public License, version 3.
 */
package org.pepsoft.worldpainter.layers.exporters;

import java.util.Random;

final class CaveSystemSimplexNoise3D {
    private static final int[] GRAD3 = new int[]{1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0, 1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1, 0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1};
    private final short[] perm = new short[512];

    public CaveSystemSimplexNoise3D(long seed) {
        int i;
        short[] p = new short[256];
        for (int i2 = 0; i2 < 256; ++i2) {
            p[i2] = (short)i2;
        }
        Random rng = new Random(seed);
        for (i = 255; i > 0; --i) {
            int j = rng.nextInt(i + 1);
            short tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (i = 0; i < 512; ++i) {
            this.perm[i] = p[i & 0xFF];
        }
    }

    private static int fastfloor(double x) {
        return x > 0.0 ? (int)x : (int)x - 1;
    }

    private static double dot(int gi, double x, double y, double z) {
        return (double)GRAD3[gi] * x + (double)GRAD3[gi + 1] * y + (double)GRAD3[gi + 2] * z;
    }

    public double noise(double xin, double yin, double zin) {
        double t3;
        double t2;
        double t1;
        int k2;
        int j2;
        int i2;
        int k1;
        int j1;
        int i1;
        double F3 = 0.3333333333333333;
        double s = (xin + yin + zin) * 0.3333333333333333;
        int i = CaveSystemSimplexNoise3D.fastfloor(xin + s);
        int j = CaveSystemSimplexNoise3D.fastfloor(yin + s);
        int k = CaveSystemSimplexNoise3D.fastfloor(zin + s);
        double G3 = 0.16666666666666666;
        double t = (double)(i + j + k) * 0.16666666666666666;
        double X0 = (double)i - t;
        double Y0 = (double)j - t;
        double Z0 = (double)k - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;
        double z0 = zin - Z0;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1;
                j1 = 0;
                k1 = 0;
                i2 = 1;
                j2 = 1;
                k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1;
                j1 = 0;
                k1 = 0;
                i2 = 1;
                j2 = 0;
                k2 = 1;
            } else {
                i1 = 0;
                j1 = 0;
                k1 = 1;
                i2 = 1;
                j2 = 0;
                k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0;
            j1 = 0;
            k1 = 1;
            i2 = 0;
            j2 = 1;
            k2 = 1;
        } else if (x0 < z0) {
            i1 = 0;
            j1 = 1;
            k1 = 0;
            i2 = 0;
            j2 = 1;
            k2 = 1;
        } else {
            i1 = 0;
            j1 = 1;
            k1 = 0;
            i2 = 1;
            j2 = 1;
            k2 = 0;
        }
        double x1 = x0 - (double)i1 + 0.16666666666666666;
        double y1 = y0 - (double)j1 + 0.16666666666666666;
        double z1 = z0 - (double)k1 + 0.16666666666666666;
        double x2 = x0 - (double)i2 + 0.3333333333333333;
        double y2 = y0 - (double)j2 + 0.3333333333333333;
        double z2 = z0 - (double)k2 + 0.3333333333333333;
        double x3 = x0 - 1.0 + 0.5;
        double y3 = y0 - 1.0 + 0.5;
        double z3 = z0 - 1.0 + 0.5;
        int ii = i & 0xFF;
        int jj = j & 0xFF;
        int kk = k & 0xFF;
        int gi0 = this.perm[ii + this.perm[jj + this.perm[kk]]] % 12 * 3;
        int gi1 = this.perm[ii + i1 + this.perm[jj + j1 + this.perm[kk + k1]]] % 12 * 3;
        int gi2 = this.perm[ii + i2 + this.perm[jj + j2 + this.perm[kk + k2]]] % 12 * 3;
        int gi3 = this.perm[ii + 1 + this.perm[jj + 1 + this.perm[kk + 1]]] % 12 * 3;
        double n0 = 0.0;
        double n1 = 0.0;
        double n2 = 0.0;
        double n3 = 0.0;
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0.0) {
            t0 *= t0;
            n0 = t0 * t0 * CaveSystemSimplexNoise3D.dot(gi0, x0, y0, z0);
        }
        if ((t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1) > 0.0) {
            t1 *= t1;
            n1 = t1 * t1 * CaveSystemSimplexNoise3D.dot(gi1, x1, y1, z1);
        }
        if ((t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2) > 0.0) {
            t2 *= t2;
            n2 = t2 * t2 * CaveSystemSimplexNoise3D.dot(gi2, x2, y2, z2);
        }
        if ((t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3) > 0.0) {
            t3 *= t3;
            n3 = t3 * t3 * CaveSystemSimplexNoise3D.dot(gi3, x3, y3, z3);
        }
        return 32.0 * (n0 + n1 + n2 + n3);
    }

    public double fractal(int octaves, double x, double y, double z, double persistence) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxValue = 0.0;
        for (int i = 0; i < octaves; ++i) {
            total += this.noise(x * frequency, y * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        return total / maxValue;
    }
}

