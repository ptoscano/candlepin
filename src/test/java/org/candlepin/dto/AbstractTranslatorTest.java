/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Base test suite for the EntityTranslator subclasses
 */
public abstract class AbstractTranslatorTest<S, D, T extends ObjectTranslator<S, D>> {
    protected static Logger log = LoggerFactory.getLogger(AbstractTranslatorTest.class);

    protected ModelTranslator modelTranslator;
    protected T translator;
    protected S source;
    protected D dest;

    @BeforeEach
    public void init() {
        this.modelTranslator = new SimpleModelTranslator();

        this.translator = this.initObjectTranslator();
        this.initModelTranslator(this.modelTranslator);
        this.source = this.initSourceObject();
        this.dest = this.initDestinationObject();
    }

    protected abstract void initModelTranslator(ModelTranslator modelTranslator);
    protected abstract T initObjectTranslator();
    protected abstract S initSourceObject();
    protected abstract D initDestinationObject();

    /**
     * Called to verify the output object contains the information from the given source object.
     * If the childrenGenerated parameter is true, any nested/children objects in the destination
     * object should be verified as well; otherwise, if childrenGenerated is false, nested objects
     * should be null.
     *
     * @param source
     *  The source object used in the translate or populate step
     *
     * @param dest
     *  The generated or populated destination object to verify
     *
     * @param childrenGenerated
     *  Whether or not children were generated/populated for the given output object
     */
    protected abstract void verifyOutput(S source, D dest, boolean childrenGenerated);

    @Test
    public void testTranslate() {
        D dto = (D) this.translator.translate(this.source);
        this.verifyOutput(this.source, dto, false);
    }

    @Test
    public void testTranslateWithNullSource() {
        D dto = (D) this.translator.translate(null);
        assertNull(dto);
    }

    @Test
    public void testTranslateWithModelTranslator() {
        D dto = (D) this.translator.translate(this.modelTranslator, this.source);
        this.verifyOutput(this.source, dto, true);
    }

    @Test
    public void testTranslateWithModelTranslatorAndNullSource() {
        D dto = (D) this.translator.translate(this.modelTranslator, null);
        assertNull(dto);
    }

    @Test
    public void testPopulate() {
        D dto = (D) this.translator.populate(this.source, this.dest);
        assertSame(dto, this.dest);
        this.verifyOutput(this.source, this.dest, false);
    }

    @Test
    public void testPopulateWithNullSource() {
        assertThrows(IllegalArgumentException.class, ()->
            this.translator.populate(null, this.dest));
    }

    @Test
    public void testPopulateWithNullDestination() {
        assertThrows(IllegalArgumentException.class, ()->
            this.translator.populate(this.source, null));
    }

    @Test
    public void testPopulateWithModelTranslator() {
        D dto = (D) this.translator.populate(this.modelTranslator, this.source, this.dest);
        assertSame(dto, this.dest);
        this.verifyOutput(this.source, this.dest, true);
    }

    @Test
    public void testPopulateWithModelTranslatorAndNullSource() {
        assertThrows(IllegalArgumentException.class, ()->
            this.translator.populate(this.modelTranslator, null, this.dest));
    }

    @Test
    public void testPopulateWithModelTranslatorAndNullDestination() {
        assertThrows(IllegalArgumentException.class, ()->
            this.translator.populate(this.modelTranslator, this.source, null));
    }
}
