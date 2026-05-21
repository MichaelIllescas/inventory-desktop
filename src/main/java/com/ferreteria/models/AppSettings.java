package com.ferreteria.models;

public class AppSettings {

    private String businessName;
    private String businessAddress;
    private String businessPhone;
    private String businessCuit;
    private String logoPath;

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

    public boolean hasLogo() {
        if (logoPath == null || logoPath.isBlank()) return false;
        return new java.io.File(logoPath).exists();
    }

    public boolean hasData() {
        return businessName != null && !businessName.isBlank();
    }
}
