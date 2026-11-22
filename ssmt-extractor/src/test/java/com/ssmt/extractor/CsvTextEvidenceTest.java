package com.ssmt.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Header-based CSV text evidence skips the rows the game drops (BridgeForge RC8_BEHAVIOUR RC8-47), and only those. */
class CsvTextEvidenceTest {

    @Test
    void aRowTheGameDropsIsNoEvidence() {
        assertThat(CsvTextEvidence.sample("name,id\n#Note row,x\nLaser,laser\n")).contains("Laser");
    }

    @Test
    void aQuotedHashFirstCellIsARowTheGameKeeps() {
        assertThat(CsvTextEvidence.sample("\uFEFFname,id\n\"#Light Mortar\",lightmortar_fighter\n")).contains("#Light Mortar");
    }
}
