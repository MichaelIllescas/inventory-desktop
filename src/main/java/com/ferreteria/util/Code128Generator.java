package com.ferreteria.util;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Generador de código de barras Code 128B en JavaFX, sin dependencias externas.
 */
public class Code128Generator {

    // Patrones de barras para Code 128 (11 bits cada uno: 1=barra, 0=espacio)
    private static final int[] PATTERNS = {
        0b11011001100, 0b11001101100, 0b11001100110, 0b10010011000, 0b10010001100,
        0b10001001100, 0b10011001000, 0b10011000100, 0b10001100100, 0b11001001000,
        0b11001000100, 0b11000100100, 0b10110011100, 0b10011011100, 0b10011001110,
        0b10111001100, 0b10011101100, 0b10011100110, 0b11001110010, 0b11001011100,
        0b11001001110, 0b11011100100, 0b11001110100, 0b11101101110, 0b11101001100,
        0b11100101100, 0b11100100110, 0b11101100100, 0b11100110100, 0b11100110010,
        0b11011011000, 0b11011000110, 0b11000110110, 0b10100011000, 0b10001011000,
        0b10001000110, 0b10110001000, 0b10001101000, 0b10001100010, 0b11010001000,
        0b11000101000, 0b11000100010, 0b10110111000, 0b10110001110, 0b10001101110,
        0b10111011000, 0b10111000110, 0b10001110110, 0b11101110110, 0b11010001110,
        0b11000101110, 0b11011101000, 0b11011100010, 0b11011101110, 0b11101011000,
        0b11101000110, 0b11100010110, 0b11101101000, 0b11101100010, 0b11100011010,
        0b11101111010, 0b11001000010, 0b11110001010, 0b10100110000, 0b10100001100,
        0b10010110000, 0b10010000110, 0b10000101100, 0b10000100110, 0b10110010000,
        0b10110000100, 0b10011010000, 0b10011000010, 0b10000110100, 0b10000110010,
        0b11000010010, 0b11001010000, 0b11110111010, 0b11000010100, 0b10001111010,
        0b10100111100, 0b10010111100, 0b10010011110, 0b10111100100, 0b10011110100,
        0b10011110010, 0b11110100100, 0b11110010100, 0b11110010010, 0b11011011110,
        0b11011110110, 0b11110110110, 0b10101111000, 0b10100011110, 0b10001011110,
        0b10111101000, 0b10111100010, 0b11110101000, 0b11110100010, 0b10111011110,
        0b10111101110, 0b11101011110, 0b11110101110, 0b11010000100, 0b11010010000,
        0b11010011100, 0b11000111010  // stop pattern
    };

    private static final int START_B = 104;
    private static final int STOP    = 106;

    /**
     * Genera un Canvas con el código de barras Code 128B del texto dado.
     * @param text  texto a codificar (ASCII 32-126)
     * @param barW  ancho de una barra unitaria en px
     * @param height alto del canvas en px
     */
    public static Canvas generate(String text, double barW, double height) {
        int[] symbols = encode(text);
        // Calcular ancho total: cada símbolo tiene 11 módulos, stop tiene 13
        int totalModules = 11 + (symbols.length - 1) * 11 + 13 + 2; // quiet zones = 2×10 módulos
        double totalWidth = (totalModules + 20) * barW;

        Canvas canvas = new Canvas(totalWidth, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, totalWidth, height);
        gc.setFill(Color.BLACK);

        double x = 10 * barW; // quiet zone izquierda
        // start B
        x = drawPattern(gc, PATTERNS[START_B], x, barW, height);
        // símbolos de datos
        for (int sym : symbols) {
            x = drawPattern(gc, PATTERNS[sym], x, barW, height);
        }
        // stop (13 módulos)
        x = drawStopPattern(gc, PATTERNS[STOP], x, barW, height);

        return canvas;
    }

    /** Genera una WritableImage (útil para ImageView). */
    public static WritableImage generateImage(String text, double barW, double height) {
        Canvas canvas = generate(text, barW, height);
        WritableImage img = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, img);
        return img;
    }

    private static int[] encode(String text) {
        int[] symbols = new int[text.length()];
        int checksum = START_B;
        for (int i = 0; i < text.length(); i++) {
            int val = text.charAt(i) - 32; // Code 128B: space=0, '!'=1, ...
            symbols[i] = val;
            checksum += (i + 1) * val;
        }
        // El checksum va al final como símbolo extra
        int[] result = new int[text.length() + 1];
        System.arraycopy(symbols, 0, result, 0, symbols.length);
        result[symbols.length] = checksum % 103;
        return result;
    }

    private static double drawPattern(GraphicsContext gc, int pattern, double x, double barW, double height) {
        for (int bit = 10; bit >= 0; bit--) {
            boolean bar = ((pattern >> bit) & 1) == 1;
            if (bar) gc.fillRect(x, 0, barW, height);
            x += barW;
        }
        return x;
    }

    private static double drawStopPattern(GraphicsContext gc, int pattern, double x, double barW, double height) {
        // Stop de Code 128 tiene 13 módulos (patrón estándar + barra final)
        for (int bit = 12; bit >= 0; bit--) {
            boolean bar = ((pattern >> bit) & 1) == 1;
            if (bar) gc.fillRect(x, 0, barW, height);
            x += barW;
        }
        return x;
    }
}
