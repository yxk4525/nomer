package org.globalbioticinteractions.nomer.cmd;

import com.beust.jcommander.Parameter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.eol.globi.domain.PropertyAndValueDictionary;
import org.eol.globi.util.ResourceUtil;
import org.globalbioticinteractions.nomer.util.TermMatcherContextCaching;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

abstract class CmdDefaultParams extends TermMatcherContextCaching implements Runnable {

    private static final Log LOG = LogFactory.getLog(CmdDefaultParams.class);
    public static final String SCHEMA_DEFAULT = "[ { \"column\": 1, \"type\": \"name\" }, {\"column\": 0, \"type\": \"externalId\" } ]";
    public static final String PROPERTIES_DEFAULT = "classpath:/org/globalbioticinteractions/nomer/default.properties";

    @Parameter(names = {"--cache-dir", "-c"}, description = "cache directory")
    private String cacheDir = "./.nomer";

    @Parameter(names = {"--schema", "-s"}, description = "sparse schema definition")
    private String schema = SCHEMA_DEFAULT;

    @Parameter(names = {"--properties", "-p"}, description = "point to properties file to override defaults.")
    private String propertiesResource = "";

    @Override
    public String getCacheDir() {
        return cacheDir;
    }

    @Override
    public String getProperty(String key) {
        Properties props = getProperties();
        return StringUtils.trim(props.getProperty(key));
    }

    Properties getProperties() {
        Properties props = new Properties(System.getProperties());
        try {
            props.load(ResourceUtil.asInputStream(PROPERTIES_DEFAULT));
            props = new Properties(props);
            if (StringUtils.isNotBlank(getPropertiesResource())) {
                File propertiesFile = new File(getPropertiesResource());
                if (propertiesFile.exists() && propertiesFile.isFile()) {
                    props.load(new FileInputStream(propertiesFile));
                } else {
                    props.load(ResourceUtil.asInputStream(getPropertiesResource()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to load properties from [" + getPropertiesResource() + "]", e);
        }

        return props;
    }

    @Parameter(description = "[matcher1] [matcher2] ...")
    private List<String> matchers = new ArrayList<>();

    @Override
    public List<String> getMatchers() {
        return matchers;
    }

    @Override
    public Map<Integer, String> getInputSchema() {
        String schema = this.schema;
        return parseSchema(schema);
    }

    public String getPropertiesResource() {
        return StringUtils.trim(propertiesResource);
    }

    static Map<Integer, String> parseSchema(String schema) {
        Map<Integer, String> schemaMap = new TreeMap<>();
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(schema);
            if (jsonNode.isArray() && jsonNode.size() > 0) {
                for (JsonNode node : jsonNode) {
                    schemaMap.put(node.get("column").asInt(), node.get("type").asText());

                }
            }
        } catch (IOException e) {
            LOG.error("failed to parse schema \"" + schema + "\", returning default \"" + SCHEMA_DEFAULT + "\" instead.", e);

        }
        return MapUtils.unmodifiableMap(schemaMap.size() < 2
                ? new TreeMap<Integer, String>() {{
            put(0, PropertyAndValueDictionary.EXTERNAL_ID);
            put(1, PropertyAndValueDictionary.NAME);
        }}
                : schemaMap);
    }
}
