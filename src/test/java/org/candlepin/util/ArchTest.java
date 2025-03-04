/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Set;


public class ArchTest {

    @Test
    public void testParseArches() {
        Set<String> arches = Arch.parseArches("x86,x86_64,ia64,s390x,ppc,s390,ppc64");
        assertTrue(arches.contains("x86"));
        assertTrue(arches.contains("x86_64"));
        assertTrue(arches.contains("ia64"));
        assertTrue(arches.contains("s390x"));
        assertTrue(arches.contains("ppc"));
        assertTrue(arches.contains("s390"));
        assertTrue(arches.contains("ppc64"));
        assertEquals(7, arches.size());
    }

    @Test
    public void testParseArchesTrailingComma() {
        Set<String> arches = Arch.parseArches("x86,x86_64,ia64,s390x,ppc,s390,ppc64,");
        assertTrue(arches.contains("x86"));
        assertTrue(arches.contains("x86_64"));
        assertTrue(arches.contains("ia64"));
        assertTrue(arches.contains("s390x"));
        assertTrue(arches.contains("ppc"));
        assertTrue(arches.contains("s390"));
        assertTrue(arches.contains("ppc64"));
        assertEquals(7, arches.size());
    }

    @Test
    public void testParseArchesLeadingComma() {
        String archString = ",x86";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("x86"));
        assertEquals(1, arches.size());
    }

    @Test
    public void testParseArchesLeadingCommas() {
        String archString = ",,,,x86";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("x86"));
        assertEquals(1, arches.size());
    }

    @Test
    public void testParseArchesMultipleCommas() {
        String archString = "x86,,,x86_64, , ,s390x";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("x86"));
        assertEquals(3, arches.size());
    }

    @Test
    public void testParseArchesMultipleSpaces() {
        String archString = "x86,  x86_64,    , ,s390x   ";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("x86"));
        assertTrue(arches.contains("x86_64"));
        assertTrue(arches.contains("s390x"));
        assertEquals(3, arches.size());
    }

    @Test
    public void testParseArchesSpuriousWhitespace() {
        String archString = "  x86 ,   x86_64,ia64,s390x,ppc,s390,  ppc64,";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("x86"));
        assertTrue(arches.contains("x86_64"));
        assertTrue(arches.contains("ia64"));
        assertTrue(arches.contains("s390x"));
        assertTrue(arches.contains("ppc"));
        assertTrue(arches.contains("s390"));
        assertTrue(arches.contains("ppc64"));
        assertEquals(7, arches.size());
    }

    @Test
    public void testParseArchesEmptyString() {
        String archString = "";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.isEmpty());
    }

    @Test
    public void testParseArchesJustComma() {
        String archString = ",";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.isEmpty());
    }

    @Test
    public void testParseArchesDoubles() {
        String archString = "x86,x86,x86";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("x86"));
        assertEquals(1, arches.size());
    }

    @Test
    public void testParseArchesAll() {
        String archString = "ALL";
        Set<String> arches = Arch.parseArches(archString);
        assertTrue(arches.contains("ALL"));
        assertEquals(1, arches.size());
    }

    @Test
    public void testContentForConsumerExactMatch() {
        assertTrue(Arch.contentForConsumer("i386", "i386"));
    }

    @Test
    public void testContentForConsumerWrongArch() {
        assertFalse(Arch.contentForConsumer("i386", "s390x"));
    }

    @Test
    public void testContentFori686ForX8664() {
        assertFalse(Arch.contentForConsumer("i686", "x86_64"));
    }

    @Test
    public void testContentForConsumerX86Fori686() {
        assertTrue(Arch.contentForConsumer("x86", "i686"));
    }

    @Test
    public void testContentForConsumerX86Fori386() {
        assertTrue(Arch.contentForConsumer("x86", "i386"));
    }

    @Test
    public void testContentForConsumerX86ForX8664() {
        assertFalse(Arch.contentForConsumer("x86", "x86_64"));
    }

    @Test
    public void testContentForConsumerX86Fors390x() {
        assertFalse(Arch.contentForConsumer("x86", "s390x"));
    }

    @Test
    public void testContentForConsumeri386Fori686() {
        assertFalse(Arch.contentForConsumer("i686", "i386"));
    }

    @Test
    public void testContentForConsumeri686Fori586() {
        // an i586 can't use i686 content. Not that
        // RHEL or fedora run on an i586, but...
        assertFalse(Arch.contentForConsumer("i686", "i586"));
    }

    @Test
    public void testContentForConsumerPpcForPpc64() {
        assertFalse(Arch.contentForConsumer("ppc", "ppc64"));
    }

    @Test
    public void testContentForConsumerOthers() {
        assertFalse(Arch.contentForConsumer("s390", "x86_64"));
        assertFalse(Arch.contentForConsumer("s390x", "x86_64"));
        assertFalse(Arch.contentForConsumer("ppc", "x86_64"));
        assertFalse(Arch.contentForConsumer("ppc64", "x86_64"));
        assertFalse(Arch.contentForConsumer("ia64", "x86_64"));
        assertFalse(Arch.contentForConsumer("arm", "x86_64"));
    }

    @Test
    public void testContentForConsumerNoarch() {
        assertTrue(Arch.contentForConsumer("noarch", "x86_64"));
        assertTrue(Arch.contentForConsumer("noarch", "i386"));
        assertTrue(Arch.contentForConsumer("noarch", "i586"));
        assertTrue(Arch.contentForConsumer("noarch", "i686"));
        assertTrue(Arch.contentForConsumer("noarch", "ia64"));
        assertTrue(Arch.contentForConsumer("noarch", "ppc"));
        assertTrue(Arch.contentForConsumer("noarch", "ppc64"));
        assertTrue(Arch.contentForConsumer("noarch", "s390x"));
        assertTrue(Arch.contentForConsumer("noarch", "s390"));
        assertTrue(Arch.contentForConsumer("noarch", "z80"));
    }

    @Test
    public void testContentForConsumerAll() {
        assertTrue(Arch.contentForConsumer("ALL", "x86_64"));
        assertTrue(Arch.contentForConsumer("ALL", "i386"));
        assertTrue(Arch.contentForConsumer("ALL", "i586"));
        assertTrue(Arch.contentForConsumer("ALL", "i686"));
        assertTrue(Arch.contentForConsumer("ALL", "ia64"));
        assertTrue(Arch.contentForConsumer("ALL", "ppc"));
        assertTrue(Arch.contentForConsumer("ALL", "ppc64"));
        assertTrue(Arch.contentForConsumer("ALL", "s390x"));
        assertTrue(Arch.contentForConsumer("ALL", "s390"));
        assertTrue(Arch.contentForConsumer("ALL", "z80"));
    }
}
