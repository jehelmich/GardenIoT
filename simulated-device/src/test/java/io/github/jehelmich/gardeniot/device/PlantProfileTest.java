package io.github.jehelmich.gardeniot.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlantProfileTest {

    @Test
    void looksUpSpeciesByIdCaseInsensitively() {
        assertThat(PlantProfile.byId("Cactus")).map(PlantProfile::name).contains("Prickly pear");
        assertThat(PlantProfile.byId("triffid")).isEmpty();
        assertThat(PlantProfile.byId(null)).isEmpty();
    }

    @Test
    void namesADeviceAfterItsSpeciesWhenItCan() {
        assertThat(PlantProfile.forDevice("mint").id()).isEqualTo("mint");
        assertThat(PlantProfile.forDevice("garden-1")).isEqualTo(PlantProfile.DEFAULT);
    }

    @Test
    void everyProfileIsInternallyConsistent() {
        for (PlantProfile p : PlantProfile.ALL) {
            assertThat(p.parchedBelow()).as(p.id()).isLessThan(p.minHumidity());
            assertThat(p.minHumidity()).as(p.id()).isLessThan(p.maxHumidity());
            assertThat(p.maxHumidity()).as(p.id()).isLessThanOrEqualTo(p.waterloggedAbove());
            assertThat(p.coldBelow()).as(p.id()).isLessThan(p.heatAbove());
            assertThat(p.wateringProfile().minHumidity()).isEqualTo(p.minHumidity());
        }
        assertThat(PlantProfile.ALL).extracting(PlantProfile::id).doesNotHaveDuplicates();
    }
}
