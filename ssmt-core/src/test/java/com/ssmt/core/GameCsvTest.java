package com.ssmt.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** BridgeForge RC8_BEHAVIOUR RC8-47: the game drops a CSV row whose raw text starts with '#'. */
class GameCsvTest {

    @Test
    void aRowStartingWithAHashIsCommentedWhateverFollows() {
        assertThat(GameCsv.commentedInGame("#Atropos,atropos\n", 0)).isTrue();
        assertThat(GameCsv.commentedInGame("name,id\n#Atropos,atropos\n", 8)).isTrue();
    }

    @Test
    void aQuotedOrIndentedHashIsARowTheGameKeeps() {
        assertThat(GameCsv.commentedInGame("\"#Light Mortar\",lightmortar_fighter\n", 0)).isFalse();
        assertThat(GameCsv.commentedInGame(" #Spaced,spaced\n", 0)).isFalse();
        assertThat(GameCsv.commentedInGame("Laser,laser\n", 0)).isFalse();
    }

    @Test
    void lineBreaksOfSkippedBlankLinesArePassedOver() {
        assertThat(GameCsv.commentedInGame("\r\n\n#Old,laser\r\n", 0)).isTrue();
        assertThat(GameCsv.commentedInGame("\r\n\nLaser,laser\r\n", 0)).isFalse();
    }

    @Test
    void aPositionAtOrPastTheEndIsNotAComment() {
        assertThat(GameCsv.commentedInGame("", 0)).isFalse();
        assertThat(GameCsv.commentedInGame("#x", 2)).isFalse();
        assertThat(GameCsv.commentedInGame("\n\n", 0)).isFalse();
    }
}
