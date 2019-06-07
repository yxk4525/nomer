package org.globalbioticinteractions.nomer.match;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eol.globi.domain.NameType;
import org.eol.globi.domain.Taxon;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.taxon.ManualSuggester;
import org.eol.globi.taxon.SuggesterFactory;
import org.eol.globi.taxon.TermMatchListener;
import org.eol.globi.taxon.TermMatcher;
import org.globalbioticinteractions.nomer.match.TermMatcherCorrectFactory;
import org.globalbioticinteractions.nomer.util.TermMatcherContext;
import org.hamcrest.core.Is;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TermMatcherCorrectFactoryTest {

    @Test
    public void init() {
        assertNotNull(new TermMatcherCorrectFactory().createTermMatcher(null));
    }

    @Test(expected = IllegalStateException.class)
    public void notInitialized() throws PropertyEnricherException {
        TermMatcher termMatcher = new TermMatcherCorrectFactory().createTermMatcher(null);
        termMatcher.findTermsForNames(Arrays.asList("copepods"), (nodeId, name, taxon, nameType) -> {
        });
    }

    @Test
    public void correct() throws PropertyEnricherException {
        TermMatcher termMatcher = new TermMatcherCorrectFactory().createTermMatcher(createTestContext());
        AtomicBoolean found = new AtomicBoolean(false);
        termMatcher.findTermsForNames(Arrays.asList("copepods"), new TermMatchListener() {
            @Override
            public void foundTaxonForName(Long nodeId, String name, Taxon taxon, NameType nameType) {
                assertThat(taxon.getName(), Is.is("Copepoda"));
                found.set(true);
            }
        });
        assertTrue(found.get());
    }

    private TermMatcherContext createTestContext() {
        return new TermMatcherContext() {
            @Override
            public String getCacheDir() {
                return null;
            }

            @Override
            public InputStream getResource(String uri) throws IOException {
                return IOUtils.toInputStream("map".equals(uri) ? "copepods,Copepoda" : "unidentified");
            }

            @Override
            public List<String> getMatchers() {
                return null;
            }

            @Override
            public Map<Integer, String> getInputSchema() {
                return null;
            }

            @Override
            public Map<Integer, String> getOutputSchema() {
                return null;
            }

            @Override
            public String getProperty(String key) {
                return new HashMap<String, String>() {{
                    put(SuggesterFactory.NOMER_TAXON_NAME_CORRECTION_URL, "map");
                    put(SuggesterFactory.NOMER_TAXON_NAME_STOPWORD_URL, "words");
                }}.get(key);
            }
        };
    }

    @Test
    public void correctSingleTermWithStopword() throws PropertyEnricherException {
        TermMatcher termMatcher = new TermMatcherCorrectFactory().createTermMatcher(createTestContext());
        AtomicBoolean found = new AtomicBoolean(false);
        termMatcher.findTermsForNames(Arrays.asList("unidentified copepods"), new TermMatchListener() {
            @Override
            public void foundTaxonForName(Long nodeId, String name, Taxon taxon, NameType nameType) {
                assertThat(taxon.getName(), Is.is("Copepoda"));
                found.set(true);
            }
        });
        assertTrue(found.get());
    }

    @Test
    public void correctSingleTermWithCapitalizedStopword() throws PropertyEnricherException {
        TermMatcher termMatcher = new TermMatcherCorrectFactory().createTermMatcher(createTestContext());
        AtomicBoolean found = new AtomicBoolean(false);
        termMatcher.findTermsForNames(Arrays.asList("Unidentified Copepods"), new TermMatchListener() {
            @Override
            public void foundTaxonForName(Long nodeId, String name, Taxon taxon, NameType nameType) {
                assertThat(taxon.getName(), Is.is("Copepoda"));
                found.set(true);
            }
        });
        assertTrue(found.get());
    }

}