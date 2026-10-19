package io.opennoodoe.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VehicleModelCatalogTest {
    @Test
    public void ak550RegionCodeMapsToPublishedModel() {
        assertEquals("AK550", VehicleModelCatalog.displayName("SAA1AA(KR)"));
    }

    @Test
    public void unknownCodeRemainsVisible() {
        assertEquals("ZZ99XX", VehicleModelCatalog.displayName("zz99xx(eu)"));
        assertEquals("KYMCO NOODOE", VehicleModelCatalog.displayName(""));
    }

    @Test
    public void knownNoodoeFamiliesMapWithoutNetwork() {
        assertEquals("KRV180", VehicleModelCatalog.displayName("SA35AC"));
        assertEquals("CV3", VehicleModelCatalog.displayName("SBA1CA"));
        assertEquals("XCITING 400", VehicleModelCatalog.displayName("SK80EA"));
    }
}
