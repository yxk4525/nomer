package org.globalbioticinteractions.nomer.match;

import org.eol.globi.domain.NameType;
import org.eol.globi.domain.TaxonImpl;
import org.eol.globi.domain.Term;
import org.eol.globi.domain.TermImpl;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.taxon.CorrectionService;
import org.eol.globi.taxon.TaxonNameCorrector;
import org.eol.globi.taxon.TermMatchListener;
import org.eol.globi.taxon.TermMatcher;
import org.globalbioticinteractions.nomer.util.TermMatcherContext;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TermMatcherCorrectFactory implements TermMatcherFactory {

    private final CorrectionService taxonNameCorrector = new TaxonNameCorrector();

    @Override
    public TermMatcher createTermMatcher(TermMatcherContext ctx) {
        return new TermMatcher() {

            @Override
            public void findTermsForNames(List<String> names, TermMatchListener termMatchListener) throws PropertyEnricherException {
                List<Term> terms = names.stream().map(name -> new TermImpl(null, name)).collect(Collectors.toList());
                this.findTerms(terms, termMatchListener);
            }

            @Override
            public void findTerms(List<Term> terms, TermMatchListener listener) throws PropertyEnricherException {
                Stream<TermImpl> correctedTerms = terms.stream()
                        .map(term -> new TermImpl(term.getId(), taxonNameCorrector.correct(term.getName())));
                correctedTerms.forEach(term -> {
                    listener.foundTaxonForName(null, term.getName(), new TaxonImpl(term.getName(), term.getId()), NameType.SAME_AS);
                });

            }
        };
    }

    @Override
    public String getName() {
        return "globi-correct";
    }

    @Override
    public String getDescription() {
        return "Scrubs names using GloBI's (taxonomic) name scrubber. Scrubbing includes removing of stopwords (e.g., undefined), correcting common typos using a \"crappy\" names list, parse to canonical name using gnparser (see https://github.com/GlobalNamesArchitecture/gnparser), and more.";
    }
}
