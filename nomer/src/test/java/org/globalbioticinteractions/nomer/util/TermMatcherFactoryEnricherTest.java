package org.globalbioticinteractions.nomer.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eol.globi.domain.NameType;
import org.eol.globi.domain.Taxon;
import org.eol.globi.domain.TermImpl;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.taxon.TermMatchListener;
import org.eol.globi.taxon.TermMatcher;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TermMatcherFactoryEnricherTest {


    private final File cacheDir = new File("./target/nomer-cache");

    @Before
    public void clean() throws IOException {
        System.out.println("clean start..");
        FileUtils.deleteQuietly(cacheDir);
        FileUtils.forceMkdir(cacheDir);
        System.out.println("clean end.");
    }

    @Test
    public void nodc() throws PropertyEnricherException {
        final URL nodcTestArchive = getClass().getResource("/org/eol/globi/taxon/nodc_archive.zip");
        assertNotNull(nodcTestArchive);

        TermMatcherContext ctx = new TermMatcherContextCaching() {

            @Override
            public String getCacheDir() {
                return cacheDir.getAbsolutePath();
            }

            @Override
            public String getProperty(String key) {
                System.out.println("for key [" + key + "]");
                if (StringUtils.equals("nodc.url", key)) {
                    String urlString = "zip:" + nodcTestArchive.toString()
                            + "!/0050418/1.1/data/0-data/NODC_TaxonomicCode_V8_CD-ROM/TAXBRIEF.DAT";
                    System.out.println("resource [" + urlString + "]");
                    return urlString;
                }
                return null;
            }

        };
        TermMatcher termMatcher = new TermMatcherFactoryEnricher().createTermMatcher(ctx);
        termMatcher.findTerms(Arrays.asList(new TermImpl("NODC:9227040101", "Mickey")), new TermMatchListener() {
            @Override
            public void foundTaxonForName(Long id, String name, Taxon taxon, NameType nameType) {
                assertThat(name, Is.is("Mickey"));
                // note that the NODC taxonomy maps to ITIS:180725 and online itis maps ITIS:180725 to ITIS:552761
                assertThat(taxon.getExternalId(), Is.is("ITIS:552761"));
            }
        });
    }
}
