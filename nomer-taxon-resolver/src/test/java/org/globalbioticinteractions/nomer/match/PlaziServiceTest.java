package org.globalbioticinteractions.nomer.match;

import org.eol.globi.domain.TaxonImpl;
import org.eol.globi.service.PropertyEnricher;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.service.TaxonUtil;
import org.globalbioticinteractions.nomer.util.TermMatcherContext;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.eol.globi.service.TaxonUtil.taxonToMap;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class PlaziServiceTest {

    @Test
    public void enrich() throws PropertyEnricherException {
        PropertyEnricher service = createService();

        TaxonImpl taxon = new TaxonImpl("Carvalhoma", null);
        Map<String, String> enriched = service.enrichFirstMatch(taxonToMap(taxon));

        assertThat(TaxonUtil.mapToTaxon(enriched).getPath(), is("Animalia | Arthropoda | Insecta | Hemiptera | Miridae | Carvalhoma"));
        assertThat(TaxonUtil.mapToTaxon(enriched).getExternalId(), is("http://taxon-concept.plazi.org/id/Animalia/Carvalhoma_Slater_1977"));
        assertThat(TaxonUtil.mapToTaxon(enriched).getName(), is("Carvalhoma"));
        assertThat(TaxonUtil.mapToTaxon(enriched).getRank(), is("genus"));
        assertThat(TaxonUtil.mapToTaxon(enriched).getPathNames(), is("kingdom | phylum | class | order | family | genus"));
    }


    @Test
    public void enrichNoMatch() throws PropertyEnricherException {
        PropertyEnricher service = createService();

        Map<String, String> enriched = service.enrichFirstMatch(
                taxonToMap(new TaxonImpl(null, "ITIS:999999999")));

        assertThat(TaxonUtil.mapToTaxon(enriched).getPath(), is(nullValue()));
    }

    @Test
    public void enrichPrefixMismatch() throws PropertyEnricherException {
        PropertyEnricher service = createService();

        Map<String, String> enriched = service.enrichFirstMatch(
                taxonToMap(new TaxonImpl(null, "FOO:2")));

        assertThat(TaxonUtil.mapToTaxon(enriched).getPath(), is(nullValue()));
    }

    private PropertyEnricher createService() {
        File file = new File("target/cache" + UUID.randomUUID());
        return new PlaziService(new TermMatcherContext() {
            @Override
            public String getCacheDir() {
                return file.getAbsolutePath();
            }

            @Override
            public InputStream getResource(String uri) throws IOException {
                return getClass().getResourceAsStream(uri);
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
                return new TreeMap<String, String>() {
                    {
                        put("plazi.treatments.archive", "/org/globalbioticinteractions/nomer/match/plazi/treatments.zip");
                    }
                }.get(key);
            }
        });
    }


}