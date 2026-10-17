package io.github.wxoled;

final class Palette {
    static final int A_MAPPED = 254;
    static final int A_PAGE = 253;
    private static final int GREY_MAX = 73;
    static final int INPUT_FIELD;
    static final int PAGE = -50331648;
    private static final int TINT_TOLERANCE = 6;
    private static final int WHITE_MIN_ALPHA = 5;
    private static final int WHITE_MAX_ALPHA = 38;
    private static final int[] KNOT_IN = {0, 25, 30, 32, WHITE_MAX_ALPHA, 44, 52, 64, 74};
    private static final int[] KNOT_OUT = {0, 0, 0, 0, 0, 24, 37, 54, 74};
    private static final int[] LUT = new int[74];

    private static int clamp(int i) {
        if (i < 0) {
            return 0;
        }
        if (i > 255) {
            return 255;
        }
        return i;
    }

    static {
        int i3 = 0;
        while (i3 <= GREY_MAX) {
            int i = 1;
            int[] iArr = KNOT_IN;
            while (iArr[i] < i3) i++;
            int i2 = iArr[i];
            int i4 = i - 1;
            int i5 = iArr[i4];
            int[] iArr2 = KNOT_OUT;
            int i6 = iArr2[i4];
            LUT[i3] = i6 + Math.round(((float) (i3 - i5) * (iArr2[i] - i6)) / (i2 - i5));
            i3++;
        }
        int i7 = LUT[30];
        int iRound = i7 + Math.round(((255 - i7) * boostAlpha(13)) / 255.0f);
        INPUT_FIELD = iRound | (iRound << 16) | (-16777216) | (iRound << 8);
    }

    private Palette() {
    }

    static int map(int i) {
        int iBoostAlpha;
        int iClamp;
        int i2 = i >>> 24;
        if (i2 == 255) {
            int i3 = (i >> 16) & 255;
            int i4 = 255 & (i >> 8);
            int i5 = i & 255;
            int iMax = Math.max(i3, Math.max(i4, i5));
            int iMin = Math.min(i3, Math.min(i4, i5));
            if (iMax > GREY_MAX || iMax - iMin > TINT_TOLERANCE) {
                return i;
            }
            if (iMin >= 16 && iMax <= 18) {
                return PAGE;
            }
            int i6 = ((i3 + i4) + i5) / 3;
            int i7 = LUT[i6] - i6;
            if (i7 == 0) {
                return i;
            }
            iBoostAlpha = (clamp(i3 + i7) << 16) | (-33554432) | (clamp(i4 + i7) << 8);
            iClamp = clamp(i5 + i7);
        } else {
            if ((i & 16777215) != 16777215 || i2 < WHITE_MIN_ALPHA || i2 > WHITE_MAX_ALPHA) {
                return i;
            }
            iBoostAlpha = boostAlpha(i2) << 24;
            iClamp = 16711422;
        }
        return iBoostAlpha | iClamp;
    }

    static boolean isBoostedWhite(int i) {
        return (16777215 & i) == 16711422 && (i >>> 24) <= boostAlpha(WHITE_MAX_ALPHA);
    }

    static boolean mapPixels(int[] iArr, boolean z) {
        int iBoostAlpha;
        int iClamp;
        int i;
        boolean z2 = false;
        for (int i2 = 0; i2 < iArr.length; i2++) {
            int i3 = iArr[i2];
            int i4 = i3 >>> 24;
            if (i4 != 0) {
                int i5 = (i3 >> 16) & 255;
                int i6 = (i3 >> 8) & 255;
                int i7 = i3 & 255;
                int iMax = Math.max(i5, Math.max(i6, i7));
                int iMin = Math.min(i5, Math.min(i6, i7));
                if (iMax <= GREY_MAX && iMax - iMin <= TINT_TOLERANCE) {
                    int i8 = ((i5 + i6) + i7) / 3;
                    int i9 = LUT[i8] - i8;
                    if (i9 != 0) {
                        iBoostAlpha = (i4 << 24) | (clamp(i5 + i9) << 16) | (clamp(i6 + i9) << 8);
                        iClamp = clamp(i7 + i9);
                        i = iBoostAlpha | iClamp;
                    } else {
                        i = i3;
                    }
                } else if (z && iMin == 255 && i4 <= WHITE_MAX_ALPHA) {
                    iBoostAlpha = boostAlpha(i4) << 24;
                    iClamp = 16777215;
                    i = iBoostAlpha | iClamp;
                } else {
                    i = i3;
                }
                if (i != i3) {
                    iArr[i2] = i;
                    z2 = true;
                }
            }
        }
        return z2;
    }

    private static int boostAlpha(int i) {
        return Math.min(255, Math.round(i * 1.8f) + 4);
    }
}
