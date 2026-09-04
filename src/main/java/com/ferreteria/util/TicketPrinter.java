package com.ferreteria.util;

import com.ferreteria.models.TicketData;
import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;

/**
 * Emite el ticket de una venta, en papel o en PDF.
 *
 * Ambas salidas comparten {@link TicketRenderer}, así que el PDF de vista previa
 * es fiel a lo que va a salir por la impresora térmica. Eso permite ajustar el
 * diseño sin tener el hardware conectado.
 */
public final class TicketPrinter {

    private TicketPrinter() {}

    /** Impresoras instaladas en el sistema, para elegir en Configuración. */
    public static List<String> listPrinters() {
        List<String> names = new ArrayList<>();
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            names.add(service.getName());
        }
        return names;
    }

    /** Nombre de la impresora predeterminada de Windows, o null si no hay ninguna. */
    public static String defaultPrinterName() {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        return service == null ? null : service.getName();
    }

    /**
     * Genera el ticket como PDF de una sola página, del ancho del rollo y del
     * alto exacto que ocupa el contenido.
     */
    public static void exportPdf(TicketData data, double paperWidthMm, Path output) {
        float width = (float) TicketRenderer.mmToPt(paperWidthMm);
        float height = TicketRenderer.measureHeight(data, paperWidthMm);

        Document document = new Document(new Rectangle(width, height), 0, 0, 0, 0);
        try (FileOutputStream out = new FileOutputStream(output.toFile())) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfContentByte canvas = writer.getDirectContent();
            Graphics2D g = canvas.createGraphics(width, height);
            try {
                TicketRenderer.render(g, data, paperWidthMm);
            } finally {
                g.dispose();
            }
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el ticket en PDF", e);
        }
    }

    /**
     * Manda el ticket a imprimir sin mostrar diálogo.
     *
     * @param printerName impresora destino; si es null o no se encuentra, usa la
     *                    predeterminada del sistema.
     */
    public static void print(TicketData data, double paperWidthMm, String printerName) {
        float width = (float) TicketRenderer.mmToPt(paperWidthMm);
        // El alto hay que fijarlo antes de saber cuanto ancho imprimible da el
        // driver. Lo medimos sobre un ancho reducido: si despues resulta mas
        // angosto, los nombres largos ocupan mas renglones y el ticket crece.
        // Sobra papel es inofensivo; faltar recorta el total.
        float height = TicketRenderer.measureHeight(data, paperWidthMm * 0.88);

        PrinterJob job = PrinterJob.getPrinterJob();
        PrintService target = findPrintService(printerName);
        try {
            if (target != null) {
                job.setPrintService(target);
            }

            // El rollo es continuo: la "página" es tan alta como el ticket.
            // Área imprimible completa, porque los márgenes ya los aplica el renderer.
            Paper paper = new Paper();
            paper.setSize(width, height);
            paper.setImageableArea(0, 0, width, height);

            PageFormat format = new PageFormat();
            format.setOrientation(PageFormat.PORTRAIT);
            format.setPaper(paper);

            // Clave: preguntarle al driver que papel acepta realmente antes de dibujar.
            // No todos admiten cualquier ancho (Windows, por ejemplo, no baja de 3"
            // = 76,2 mm, asi que un rollo de 80 mm se recorta). Si le pasamos al
            // Printable nuestro PageFormat sin validar, dibujamos sobre un ancho que
            // el driver no va a imprimir y el ticket sale cortado a la derecha.
            PageFormat validated = job.validatePage(format);

            job.setJobName("Ticket" + (data.getSaleId() == null ? "" : " N " + data.getSaleId()));
            job.setPrintable(new TicketPrintable(data, paperWidthMm), validated);

            // Ademas pedimos el area imprimible explicitamente: las termicas la
            // respetan y evita que el driver aplique margenes de hoja A4.
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            attributes.add(new MediaPrintableArea(
                    (float) (validated.getImageableX() / 72f),
                    (float) (validated.getImageableY() / 72f),
                    (float) (validated.getImageableWidth() / 72f),
                    (float) (validated.getImageableHeight() / 72f),
                    MediaPrintableArea.INCH));
            AppLogger.info("TicketPrinter", "print", String.format(
                    "Enviando job: pedido=%.2f pt | validado hoja=%.2f imageable x=%.2f w=%.2f",
                    width, validated.getWidth(),
                    validated.getImageableX(), validated.getImageableWidth()));
            job.print(attributes);
        } catch (Exception e) {
            AppLogger.error("TicketPrinter", "print", "Fallo el job de impresion", e);
            throw new RuntimeException("No se pudo imprimir el ticket: " + e, e);
        }
    }

    private static PrintService findPrintService(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equalsIgnoreCase(printerName.trim())) {
                return service;
            }
        }
        // La impresora guardada ya no está: mejor caer en la predeterminada que fallar.
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    /** Puente entre PrinterJob y el renderer. El ticket siempre es una sola página. */
    private static class TicketPrintable implements Printable {

        private final TicketData data;
        private final double paperWidthMm;

        TicketPrintable(TicketData data, double paperWidthMm) {
            this.data = data;
            this.paperWidthMm = paperWidthMm;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g = (Graphics2D) graphics;

            // Usamos todo el ancho imprimible que declara el driver.
            //
            // Ese ancho no tiene por que estar centrado en la hoja: los drivers suelen
            // dejar un margen fisico no imprimible de un solo lado (Microsoft Print to
            // PDF deja 9 mm a la derecha). Ese aire es inalcanzable: no se puede
            // dibujar ahi. Achicar el ticket para compensarlo lo haria ver centrado en
            // la vista previa, pero desperdiciaria rollo en la impresora real, donde
            // los margenes si son simetricos.
            double sheet = pageFormat.getWidth();
            double x0 = pageFormat.getImageableX();
            // Algunos drivers reportan un area que se pasa del borde de la hoja.
            double w = Math.min(pageFormat.getImageableWidth(), sheet - x0);

            double drawnPt = w > 0 ? w : TicketRenderer.mmToPt(paperWidthMm);
            double startX = x0;
            double effectiveMm = TicketRenderer.ptToMm(drawnPt);

            // Valores reales del driver, para diagnosticar recortes o corrimientos
            // sin tener que adivinar mirando el PDF resultante.
            AppLogger.info("TicketPrinter", "print", String.format(
                    "PageFormat: hoja=%.2fx%.2f pt | imageable x=%.2f y=%.2f w=%.2f h=%.2f pt"
                            + " | rollo pedido=%.1f mm | dibujado=%.2f mm | startX=%.2f pt",
                    pageFormat.getWidth(), pageFormat.getHeight(),
                    pageFormat.getImageableX(), pageFormat.getImageableY(),
                    pageFormat.getImageableWidth(), pageFormat.getImageableHeight(),
                    paperWidthMm, effectiveMm, startX));

            g.translate(startX, pageFormat.getImageableY());
            TicketRenderer.render(g, data, effectiveMm);
            return PAGE_EXISTS;
        }
    }
}
