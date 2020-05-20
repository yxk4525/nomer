package org.eol.globi.taxon;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.eol.globi.data.CharsetConstant;
import org.eol.globi.domain.NameType;
import org.eol.globi.domain.PropertyAndValueDictionary;
import org.eol.globi.domain.Taxon;
import org.eol.globi.domain.TaxonImpl;
import org.eol.globi.domain.TaxonomyProvider;
import org.eol.globi.domain.Term;
import org.eol.globi.domain.TermImpl;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.service.TaxonUtil;
import org.eol.globi.tool.TermRequestImpl;
import org.eol.globi.util.CSVTSVUtil;
import org.eol.globi.util.HttpUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GlobalNamesService2 extends PropertyEnricherSimple implements TermMatcher {
    private static final Log LOG = LogFactory.getLog(GlobalNamesService2.class);
    public static final List<Integer> MATCH_TYPES_EXACT = Arrays.asList(1, 2, 6);

    private final List<GlobalNamesSources2> sources;
    private boolean includeCommonNames = false;

    public GlobalNamesService2() {
        this(GlobalNamesSources2.ITIS);
    }

    public GlobalNamesService2(GlobalNamesSources2 source) {
        this(Collections.singletonList(source));
    }

    public GlobalNamesService2(List<GlobalNamesSources2> sources) {
        super();
        this.sources = sources;
    }

    public void setIncludeCommonNames(boolean includeCommonNames) {
        this.includeCommonNames = includeCommonNames;
    }

    public boolean shouldIncludeCommonNames() {
        return this.includeCommonNames;
    }

    @Override
    public Map<String, String> enrich(Map<String, String> properties) throws PropertyEnricherException {
        Map<String, String> enrichedProperties = new HashMap<String, String>();
        final List<Taxon> exactMatches = new ArrayList<Taxon>();
        final List<Taxon> synonyms = new ArrayList<Taxon>();
        findTermsForNames(Collections.singletonList(new TermImpl(null, properties.get(PropertyAndValueDictionary.NAME))), new TermMatchListener() {
            @Override
            public void foundTaxonForTerm(Long nodeId, Term name, Taxon taxon, NameType nameType) {
                if (NameType.SAME_AS.equals(nameType)) {
                    exactMatches.add(taxon);
                } else if (NameType.SYNONYM_OF.equals(nameType)) {
                    synonyms.add(taxon);
                }
            }
        });

        if (exactMatches.size() > 0) {
            enrichedProperties.putAll(TaxonUtil.taxonToMap(exactMatches.get(0)));
        } else if (synonyms.size() == 1) {
            enrichedProperties.putAll(TaxonUtil.taxonToMap(synonyms.get(0)));
        }

        return Collections.unmodifiableMap(enrichedProperties);
    }

    @Override
    public void match(List<Term> terms, TermMatchListener termMatchListener) throws PropertyEnricherException {
        if (terms.size() == 0) {
            throw new IllegalArgumentException("need non-empty list of names");
        }
        findTermsForNames(terms, termMatchListener);
    }

    private void findTermsForNames(List<Term> terms, TermMatchListener termMatchListener) throws PropertyEnricherException {
        if (terms.size() == 0) {
            throw new IllegalArgumentException("need non-empty list of names");
        }

        try {
            URI uri = buildPostRequestURI(sources);
            try {
                parseResult(termMatchListener, executeQuery(terms, uri));
            } catch (IOException e) {
                if (terms.size() > 1) {
                    LOG.warn("retrying names query one name at a time: failed to perform batch query", e);
                    List<String> namesFailed = new ArrayList<>();
                    for (Term term : terms) {
                        try {
                            parseResult(termMatchListener, executeQuery(Collections.singletonList(term), uri));
                        } catch (IOException e1) {
                            namesFailed.add(term.getName());
                        }
                    }
                    if (namesFailed.size() > 0) {
                        throw new PropertyEnricherException("Failed to execute individual name queries for [" + StringUtils.join(namesFailed, "|") + "]");
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new PropertyEnricherException("Failed to query", e);
        }

    }

    private String executeQuery(List<Term> terms, URI uri) throws IOException {
        HttpClient httpClient = HttpUtil.getHttpClient();
        HttpPost post = new HttpPost(uri);

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        String requestIdNamePairs = terms
                .stream()
                .map(x -> x instanceof TermRequestImpl
                        ? String.format("%d|%s", ((TermRequestImpl) x).getNodeId(), x.getName())
                        : x.getName()
                ).collect(Collectors.joining("\n"));
        params.add(new BasicNameValuePair("data", requestIdNamePairs));
        post.setEntity(new UrlEncodedFormEntity(params, CharsetConstant.UTF8));

        return httpClient.execute(post, new BasicResponseHandler());
    }

    private URI buildPostRequestURI(List<GlobalNamesSources2> sources) throws URISyntaxException {
        List<String> sourceIds = new ArrayList<String>();
        for (GlobalNamesSources2 globalNamesSources2 : sources) {
            sourceIds.add(Integer.toString(globalNamesSources2.getId()));
        }

        String query = "data_source_ids=" + StringUtils.join(sourceIds, "|");
        if (shouldIncludeCommonNames()) {
            query = "with_vernaculars=true&" + query;
        }

        return new URI("https", "resolver.globalnames.org"
                , "/name_resolvers.json"
                , query
                , null);
    }

    private void parseResult(TermMatchListener termMatchListener, String result) throws PropertyEnricherException {
        try {
            parseResultNode(termMatchListener, new ObjectMapper().readTree(result));
        } catch (IOException ex) {
            throw new PropertyEnricherException("failed to parse json string [" + result + "]", ex);
        }
    }

    private void parseResultNode(TermMatchListener termMatchListener, JsonNode jsonNode) {

        JsonNode dataList = jsonNode.get("data");
        if (dataList != null && dataList.isArray()) {
            for (JsonNode data : dataList) {
                JsonNode results = data.get("results");
                if (results == null) {
                    if (dataList.size() > 0) {
                        JsonNode firstDataElement = dataList.get(0);
                        firstDataElement.get("supplied_name_string");
                        if (firstDataElement.has("is_known_name")
                                && firstDataElement.has("supplied_name_string")
                                && !firstDataElement.get("is_known_name").asBoolean(false)) {
                            noMatch(termMatchListener, data);
                        }
                    }
                } else if (results.isArray()) {
                    for (JsonNode aResult : results) {
                        TaxonomyProvider provider = getTaxonomyProvider(aResult);
                        if (provider == null) {
                            LOG.warn("found unsupported data_source_id");
                        } else {
                            if (aResult.has("classification_path")
                                    && aResult.has("classification_path_ranks")) {
                                parseClassification(termMatchListener, data, aResult, provider);
                            } else {
                                noMatch(termMatchListener, data);
                            }
                        }
                    }
                }
            }
        }
    }

    private void noMatch(TermMatchListener termMatchListener, JsonNode data) {
        String suppliedNameString = getSuppliedNameString(data);
        termMatchListener.foundTaxonForTerm(requestId(data), new TermImpl(null, suppliedNameString), new TaxonImpl(suppliedNameString), NameType.NONE);
    }

    private String parsePathIds(String list) {
        return parsePathIds(list, null);
    }

    private String parsePathIds(String list, String prefix) {
        String[] split = StringUtils.splitPreserveAllTokens(list, "|");
        List<String> ids = Collections.emptyList();
        if (split != null) {
            ids = Arrays.asList(split);
        }
        if (StringUtils.isNotBlank(prefix)) {
            List<String> prefixed = new ArrayList<String>();
            for (String id : ids) {
                if (StringUtils.startsWith(id, "gn:")) {
                    prefixed.add("");
                } else {
                    prefixed.add(prefix + scrubIds(id));
                }
            }
            ids = prefixed;
        }
        return StringUtils.join(ids, CharsetConstant.SEPARATOR);
    }

    private String scrubIds(String s) {
        return StringUtils.replace(s, "urn:lsid:marinespecies.org:taxname:", "");
    }

    public static boolean pathTailRepetitions(Taxon taxon) {
        boolean repetitions = false;
        if (StringUtils.isNotBlank(taxon.getPath())) {
            String[] split = StringUtils.split(taxon.getPath(), CharsetConstant.SEPARATOR_CHAR);
            if (split.length > 2
                    && repeatInTail(split)) {
                repetitions = true;
            }
        }
        return repetitions;
    }

    private static boolean repeatInTail(String[] split) {
        String last = StringUtils.trim(split[split.length - 1]);
        String secondToLast = StringUtils.trim(split[split.length - 2]);
        return StringUtils.equals(last, secondToLast);
    }


    protected void parseClassification(TermMatchListener termMatchListener, JsonNode data, JsonNode aResult, TaxonomyProvider provider) {
        Taxon taxon = new TaxonImpl();
        String classificationPath = aResult.get("classification_path").asText();
        taxon.setPath(parsePathIds(classificationPath));

        if (aResult.has("classification_path_ids")) {
            String classificationPathIds = aResult.get("classification_path_ids").asText();
            taxon.setPathIds(parsePathIds(classificationPathIds, provider.getIdPrefix()));
        }
        String pathRanks = aResult.get("classification_path_ranks").asText();
        taxon.setPathNames(parsePathIds(pathRanks));
        String[] ranks = CSVTSVUtil.splitPipes(pathRanks);
        if (ranks.length > 0) {
            String rank = ranks[ranks.length - 1];
            taxon.setRank(rank);
        }
        String[] taxonNames = CSVTSVUtil.splitPipes(classificationPath);
        if (ranks.length > 0 && taxonNames.length > 0) {
            String taxonName = taxonNames[taxonNames.length - 1];
            taxon.setName(taxonName);
        } else {
            taxon.setName(aResult.get("canonical_form").asText());
        }

        String taxonIdLabel = aResult.has("current_taxon_id") ? "current_taxon_id" : "taxon_id";
        String taxonIdValue = aResult.get(taxonIdLabel).asText();
        // see https://github.com/GlobalNamesArchitecture/gni/issues/35
        if (!StringUtils.startsWith(taxonIdValue, "gn:")) {
            String externalId = provider.getIdPrefix() + scrubIds(taxonIdValue);
            taxon.setExternalId(externalId);
            String suppliedNameString = getSuppliedNameString(data);

            boolean isExactMatch = aResult.has("match_type")
                    && MATCH_TYPES_EXACT.contains(aResult.get("match_type").getIntValue());

            NameType nameType = isExactMatch ? NameType.SAME_AS : NameType.SIMILAR_TO;
            if (isExactMatch && aResult.has("current_name_string")) {
                nameType = NameType.SYNONYM_OF;
            }

            // related to https://github.com/GlobalNamesArchitecture/gni/issues/48
            if (!pathTailRepetitions(taxon) && !speciesNoGenus(taxon)) {
                termMatchListener.foundTaxonForTerm(requestId(data), new TermImpl(null, suppliedNameString), taxon, nameType);
            }
        }

        if (aResult.has("vernaculars")) {
            List<String> commonNames = new ArrayList<String>();
            JsonNode vernaculars = aResult.get("vernaculars");
            for (JsonNode vernacular : vernaculars) {
                if (vernacular.has("name") && vernacular.has("language")) {
                    String name = vernacular.get("name").asText();
                    String language = vernacular.get("language").asText();
                    if (!StringUtils.equals(name, "null") && !StringUtils.equals(language, "null")) {
                        commonNames.add(vernacular.get("name").asText() + " @" + language);
                    }
                }
            }
            if (commonNames.size() > 0) {
                taxon.setCommonNames(StringUtils.join(commonNames, CharsetConstant.SEPARATOR));
            }
        }
    }

    private boolean speciesNoGenus(Taxon taxon) {
        return taxon.getPathNames().contains("species")
                && !taxon.getPathNames().contains("genus");
    }

    private String getSuppliedNameString(JsonNode data) {
        return data.get("supplied_name_string").getTextValue();
    }

    private Long requestId(JsonNode data) {
        return data.has("supplied_id") ? data.get("supplied_id").asLong() : null;
    }

    private TaxonomyProvider getTaxonomyProvider(JsonNode aResult) {
        TaxonomyProvider provider = null;
        if (aResult.has("data_source_id")) {
            int sourceId = aResult.get("data_source_id").getIntValue();

            GlobalNamesSources2[] values = GlobalNamesSources2.values();
            for (GlobalNamesSources2 value : values) {
                if (value.getId() == sourceId) {
                    provider = value.getProvider();
                    break;
                }
            }
        }
        return provider;
    }

    public List<GlobalNamesSources2> getSources() {
        return sources;
    }

    public void shutdown() {

    }
}
