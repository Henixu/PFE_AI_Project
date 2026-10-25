package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.soprahr.interne.exception.BusinessRuleException;

class DocumentFilePolicyTest {

    @Test
    void cleanFileNameKeepsOnlyTheLastPathSegment() {
        assertEquals("rapport.pdf", DocumentFilePolicy.cleanFileName("C:\\Users\\x\\rapport.pdf"));
        assertEquals("passwd.pdf", DocumentFilePolicy.cleanFileName("../../etc/passwd.pdf"));
        assertEquals("note.txt", DocumentFilePolicy.cleanFileName("  note.txt  "));
    }

    @Test
    void cleanFileNameRemovesControlAndDirectionalCharacters() {
        // U+202E (inversion du sens d'écriture) sert à faire passer « fdp.exe » pour une extension .pdf
        assertEquals("facturefdp.pdf", DocumentFilePolicy.cleanFileName("facture\u202Efdp.pdf"));
        assertEquals("ab.pdf", DocumentFilePolicy.cleanFileName("a\u0000b\n.pdf"));
    }

    @Test
    void cleanFileNameReplacesReservedCharactersAndLeadingDots() {
        assertEquals("a_b_c_d.pdf", DocumentFilePolicy.cleanFileName("a<b>c:d.pdf"));
        assertEquals("hidden.pdf", DocumentFilePolicy.cleanFileName("...hidden.pdf"));
    }

    @Test
    void cleanFileNameRejectsEmptyAndTooLongNames() {
        assertThrows(BusinessRuleException.class, () -> DocumentFilePolicy.cleanFileName(null));
        assertThrows(BusinessRuleException.class, () -> DocumentFilePolicy.cleanFileName("   "));
        assertThrows(BusinessRuleException.class, () -> DocumentFilePolicy.cleanFileName("dir/"));
        assertThrows(BusinessRuleException.class, () -> DocumentFilePolicy.cleanFileName("a".repeat(252) + ".pdf"));
        assertEquals(255, DocumentFilePolicy.cleanFileName("a".repeat(251) + ".pdf").length());
    }

    @Test
    void typeIsResolvedFromTheExtensionIgnoringCase() {
        assertEquals("application/pdf", DocumentFilePolicy.typeOf("Contrat.PDF").contentType());
        assertEquals("image/jpeg", DocumentFilePolicy.typeOf("photo.jpeg").contentType());
        assertEquals("pdf", DocumentFilePolicy.typeOf("archive.tar.pdf").extension());
    }

    @Test
    void unlistedOrMissingExtensionsAreRejected() {
        for (String name : new String[] { "virus.exe", "script.sh", "page.html", "archive.zip", "noextension", "trap.pdf.exe",
                "ends-with-dot.", "pdf" }) {
            assertThrows(BusinessRuleException.class, () -> DocumentFilePolicy.typeOf(name), name);
        }
    }

    @Test
    void contentMustMatchTheDeclaredExtension() {
        assertTrue(DocumentFilePolicy.typeOf("a.pdf").matches("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(DocumentFilePolicy.typeOf("a.pdf").matches("MZ executable".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(DocumentFilePolicy.typeOf("a.pdf").matches(new byte[0]));

        assertTrue(DocumentFilePolicy.typeOf("a.png").matches(
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0 }));
        assertFalse(DocumentFilePolicy.typeOf("a.png").matches(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }));
        assertTrue(DocumentFilePolicy.typeOf("a.jpg").matches(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 }));

        byte[] zip = { 0x50, 0x4B, 0x03, 0x04, 0 };
        assertTrue(DocumentFilePolicy.typeOf("a.docx").matches(zip));
        assertTrue(DocumentFilePolicy.typeOf("a.xlsx").matches(zip));
        assertFalse(DocumentFilePolicy.typeOf("a.docx").matches("plain".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void textFilesMustNotContainNullBytes() {
        assertTrue(DocumentFilePolicy.typeOf("a.txt").matches("bonjour é".getBytes(StandardCharsets.UTF_8)));
        assertFalse(DocumentFilePolicy.typeOf("a.txt").matches(new byte[] { 'a', 0, 'b' }));
    }
}
