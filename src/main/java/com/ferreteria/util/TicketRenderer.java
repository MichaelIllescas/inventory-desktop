package com.ferreteria.util;

import com.ferreteria.models.AppSettings;
import com.ferreteria.models.TicketData;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja el ticket sobre un {@link Graphics2D} genérico.
 *
 * Al no depender del destino, el mismo layout sirve para el PDF de vista previa
 * (Graphics2D de OpenPDF) y para la impresión real (Graphics2D de PrinterJob).
 * Así lo que se ve en el PDF es exactamente lo que sale por la impresora.
 *
 * Todas las medidas están en puntos PostScript (1 pt = 1/72"), que es la unidad
 * nativa tanto del PDF como de la API de impresión de Java.
 */
public final class TicketRenderer {

    /** Anchos de rollo más comunes en impresoras de comanda. */
    public static final double PAPER_80MM = 80;
    public static final double PAPER_58MM = 58;

    private static final double MM_TO_PT = 72.0 / 25.4;

    private static final float MARGIN = 8f;
    private static final float LOGO_MAX_HEIGHT = 46f;
    private static final float SEPARATOR_GAP = 5f;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String FISCAL_NOTE = "Documento no válido como comprobante fiscal";

    private TicketRenderer() {}

    public static double mmToPt(double mm) {
        return mm * MM_TO_PT;
    }

    public static double ptToMm(double pt) {
        return pt / MM_TO_PT;
    }

    /** Alto en puntos que ocupará el ticket con este ancho de papel. */
    public static float measureHeight(TicketData data, double paperWidthMm) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            return render(g, data, paperWidthMm, false);
        } finally {
            g.dispose();
        }
    }

    /** Dibuja el ticket y devuelve el alto ocupado en puntos. */
    public static float render(Graphics2D g, TicketData data, double paperWidthMm) {
        return render(g, data, paperWidthMm, true);
    }

    private static float render(Graphics2D g, TicketData data, double paperWidthMm, boolean paint) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);

        float pageWidth = (float) mmToPt(paperWidthMm);
        float left = MARGIN;
        float right = pageWidth - MARGIN;
        float width = right - left;
        boolean narrow = paperWidthMm < 70;

        Font fBusiness = new Font(Font.SANS_SERIF, Font.BOLD, narrow ? 10 : 12);
        Font fSmall    = new Font(Font.SANS_SERIF, Font.PLAIN, narrow ? 6 : 7);
        Font fMeta     = new Font(Font.SANS_SERIF, Font.PLAIN, narrow ? 6 : 7);
        Font fItem     = new Font(Font.SANS_SERIF, Font.PLAIN, narrow ? 7 : 8);
        Font fItemBold = new Font(Font.SANS_SERIF, Font.BOLD, narrow ? 7 : 8);
        Font fTotal    = new Font(Font.SANS_SERIF, Font.BOLD, narrow ? 12 : 14);
        Font fFooter   = new Font(Font.SANS_SERIF, Font.PLAIN, narrow ? 5 : 6);

        AppSettings settings = data.getSettings();
        float y = MARGIN;

        // Logo centrado: el del negocio si lo configuró, si no el de la app.
        {
            BufferedImage logo = (settings != null && settings.hasLogo())
                    ? readLogo(settings.getLogoPath())
                    : defaultLogo();
            if (logo != null) {
                float maxW = width * 0.72f;
                float scale = Math.min(maxW / logo.getWidth(), LOGO_MAX_HEIGHT / logo.getHeight());
                float w = logo.getWidth() * scale;
                float h = logo.getHeight() * scale;
                if (paint) {
                    g.drawImage(logo, Math.round(left + (width - w) / 2f), Math.round(y),
                            Math.round(w), Math.round(h), null);
                }
                y += h + 6f;
            }
        }

        // Encabezado del negocio
        if (settings != null && settings.hasData()) {
            y = drawCentered(g, paint, settings.getBusinessName(), fBusiness, left, width, y);
            y += 1f;
            y = drawCenteredWrapped(g, paint, settings.getBusinessAddress(), fSmall, left, width, y);
            y = drawCenteredWrapped(g, paint, settings.getBusinessPhone(), fSmall, left, width, y);
            if (notBlank(settings.getBusinessCuit())) {
                y = drawCenteredWrapped(g, paint, "CUIT: " + settings.getBusinessCuit().trim(),
                        fSmall, left, width, y);
            }
        }

        y = separator(g, paint, left, right, y);

        // Fecha, hora, comprobante, pago y cliente
        String fecha = data.getDateTime().format(DATE_FMT);
        String hora = data.getDateTime().format(TIME_FMT);
        y = drawRow(g, paint, "Fecha: " + fecha, "Hora: " + hora, fMeta, left, right, y);
        if (data.getSaleId() != null) {
            y = drawRow(g, paint, "Comprobante N° " + data.getSaleId(), "", fMeta, left, right, y);
        }
        if (notBlank(data.getPaymentMethod())) {
            y = drawRow(g, paint, "Pago: " + data.getPaymentMethod(), "", fMeta, left, right, y);
        }
        if (notBlank(data.getCustomerName())) {
            y = drawRow(g, paint, "Cliente: " + data.getCustomerName(), "", fMeta, left, right, y);
        }

        y = separator(g, paint, left, right, y);

        // Ítems: nombre en un renglón, cantidad/precio y subtotal en el siguiente.
        // En 80 mm no entra una tabla de cuatro columnas que se lea bien.
        for (TicketData.Line line : data.getLines()) {
            y = drawWrapped(g, paint, line.getName(), fItem, left, width, y);
            String qty = formatQuantity(line.getQuantity()) + " x " + formatAmount(line.getUnitPrice());
            y = drawRow(g, paint, "   " + qty, formatAmount(line.getSubtotal()), fItem, left, right, y);
            y += 2f;
        }

        y = separator(g, paint, left, right, y);

        // Total
        y = drawRow(g, paint, "TOTAL", "$ " + formatAmount(data.getTotal()), fTotal, left, right, y);
        y += 4f;

        y = separator(g, paint, left, right, y);

        // Pie
        y += 2f;
        y = drawCenteredWrapped(g, paint, "¡Gracias por su compra!", fItemBold, left, width, y);
        y += 3f;
        g.setColor(Color.BLACK);
        y = drawCenteredWrapped(g, paint, FISCAL_NOTE, fFooter, left, width, y);

        // Espacio final para que el corte de papel no muerda el texto.
        y += 24f;
        return y;
    }

    private static float drawCentered(Graphics2D g, boolean paint, String text, Font font,
                                      float left, float width, float y) {
        if (!notBlank(text)) return y;
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        float x = left + (width - fm.stringWidth(text)) / 2f;
        if (paint) g.drawString(text, x, y + fm.getAscent());
        return y + fm.getHeight();
    }

    private static float drawCenteredWrapped(Graphics2D g, boolean paint, String text, Font font,
                                             float left, float width, float y) {
        if (!notBlank(text)) return y;
        g.setFont(font);
        for (String piece : wrap(g, text.trim(), width)) {
            y = drawCentered(g, paint, piece, font, left, width, y);
        }
        return y;
    }

    private static float drawWrapped(Graphics2D g, boolean paint, String text, Font font,
                                     float left, float width, float y) {
        if (!notBlank(text)) return y;
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        for (String piece : wrap(g, text.trim(), width)) {
            if (paint) g.drawString(piece, left, y + fm.getAscent());
            y += fm.getHeight();
        }
        return y;
    }

    /** Texto a la izquierda y texto a la derecha en el mismo renglón. */
    private static float drawRow(Graphics2D g, boolean paint, String leftText, String rightText,
                                 Font font, float left, float right, float y) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        if (paint) {
            if (notBlank(leftText)) g.drawString(leftText, left, y + fm.getAscent());
            if (notBlank(rightText)) {
                g.drawString(rightText, right - fm.stringWidth(rightText), y + fm.getAscent());
            }
        }
        return y + fm.getHeight();
    }

    private static float separator(Graphics2D g, boolean paint, float left, float right, float y) {
        y += SEPARATOR_GAP;
        if (paint) {
            Stroke previous = g.getStroke();
            g.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{2f, 2f}, 0f));
            g.draw(new Line2D.Float(left, y, right, y));
            g.setStroke(previous);
        }
        return y + SEPARATOR_GAP;
    }

    private static List<String> wrap(Graphics2D g, String text, float maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    /** Logo de la app, usado cuando el negocio no configuró uno propio. */
    private static final String DEFAULT_LOGO_RESOURCE = "/images/app_icon.png";
    private static BufferedImage defaultLogo;
    private static boolean defaultLogoLoaded;

    private static synchronized BufferedImage defaultLogo() {
        if (!defaultLogoLoaded) {
            defaultLogoLoaded = true;
            try (InputStream in = TicketRenderer.class.getResourceAsStream(DEFAULT_LOGO_RESOURCE)) {
                defaultLogo = in == null ? null : ImageIO.read(in);
            } catch (Exception e) {
                defaultLogo = null;
            }
        }
        return defaultLogo;
    }

    private static BufferedImage readLogo(String path) {
        try {
            File file = new File(path);
            return file.isFile() ? ImageIO.read(file) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** "1.234,56" — mismo formato que el resto de la app. */
    static String formatAmount(double value) {
        String num = String.format("%.2f", value).replace('.', ',');
        int i = num.indexOf(',');
        if (i > 3) {
            StringBuilder sb = new StringBuilder(num);
            for (int j = i - 3; j > 0; j -= 3) {
                sb.insert(j, '.');
            }
            num = sb.toString();
        }
        return num;
    }

    /** Cantidades enteras sin decimales, fraccionadas con coma. */
    static String formatQuantity(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.3f", value).replace('.', ',')
                .replaceAll("0+$", "").replaceAll(",$", "");
    }
}
