package org.globalbioticinteractions.nomer.util;

import org.eol.globi.domain.Taxon;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.service.TaxonUtil;
import org.eol.globi.service.TermMatcherHierarchical;
import org.eol.globi.taxon.RowHandler;
import org.eol.globi.taxon.TermMatcher;
import org.eol.globi.util.CSVTSVUtil;
import org.globalbioticinteractions.nomer.match.TermMatcherRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public class MatchUtil {

    public static void match(RowHandler handler) {
        try {
            apply(System.in, handler);
        } catch (IOException | PropertyEnricherException e) {
            throw new RuntimeException("failed to apply taxon", e);
        }
    }

    public static TermMatcher getTermMatcher(List<String> matcherIds, TermMatcherContext ctx) {
        final Stream<TermMatcher> matchers =
                matcherIds
                        .stream()
                        .map(matcherId -> {
                            TermMatcher e = TermMatcherRegistry.termMatcherFor(matcherId, ctx);
                            return Optional.ofNullable(e);
                        }).filter(Optional::isPresent)
                        .map(Optional::get);

        Optional<TermMatcher> firstMatcher = matchers.findFirst();
        TermMatcher termMatcher = firstMatcher.orElseGet(() -> TermMatcherRegistry.defaultMatcher(ctx));
        return new TermMatcherHierarchical(termMatcher);
    }

    public static void apply(InputStream is, RowHandler rowHandler) throws IOException, PropertyEnricherException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] row = CSVTSVUtil.splitTSV(line);
            rowHandler.onRow(row);
        }
    }

    public static Taxon asTaxon(String[] row, Map<Integer, String> schema) {
        Map<String, String> taxonMap = new TreeMap<>();
        for (Map.Entry<Integer, String> indexType : schema.entrySet()) {
            Integer key = indexType.getKey();
            if (row.length > key) {
                taxonMap.put(indexType.getValue(), row[key]);
            }
        }
        return TaxonUtil.mapToTaxon(taxonMap);
    }
}
