package com.ferreteria.models;

public class AppSettings {

    private String businessName;
    private String businessAddress;
    private String businessPhone;
    private String businessCuit;
    private String logoPath;
    /** Impresora de tickets. Vacío = usar la predeterminada de Windows. */
    private String ticketPrinter;
    /** Ancho del rollo en milímetros (80 o 58). */
    private double ticketPaperWidthMm = 80;

    public String getBusinessName()    { return businessName; }
    public void setBusinessName(String v)    { this.businessName = v; }

    public String getBusinessAddress() { return businessAddress; }
    public void setBusinessAddress(String v) { this.businessAddress = v; }

    public String getBusinessPhone()   { return businessPhone; }
    public void setBusinessPhone(String v)   { this.businessPhone = v; }

    public String getBusinessCuit()    { return businessCuit; }
    public void setBusinessCuit(String v)    { this.businessCuit = v; }

    public String getLogoPath()        { return logoPath; }
    public void setLogoPath(String v)        { this.logoPath = v; }

    public String getTicketPrinter()          { return ticketPrinter; }
    public void setTicketPrinter(String v)    { this.ticketPrinter = v; }

    public double getTicketPaperWidthMm()     { return ticketPaperWidthMm; }
    public void setTicketPaperWidthMm(double v) {
        // Solo aceptamos los dos anchos de rollo estándar.
        this.ticketPaperWidthMm = (v == 58) ? 58 : 80;
    }

    public boolean hasLogo() {
        if (logoPath == null || logoPath.isBlank()) return false;
        return new java.io.File(logoPath).exists();
    }

    public boolean hasData() {
        return businessName != null && !businessName.isBlank();
    }
}
