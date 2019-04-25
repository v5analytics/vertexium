package org.vertexium.accumulo.iterator;

import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.Filter;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.hadoop.io.Text;
import org.vertexium.ElementFilter;
import org.vertexium.accumulo.iterator.util.SetOfStringsEncoder;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class HasAuthorizationFilter extends Filter {
    private static final String SETTING_AUTHORIZATION_TO_MATCH = "authorizationToMatch";
    private static final String SETTING_FILTERS = "filters";
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^A-Za-z0-9_\\-\\.]");
    private String authorizationToMatch;
    private EnumSet<ElementFilter> filters;
    private Map<Text, Boolean> matchCache;

    public static void setAuthorizationToMatch(IteratorSetting settings, String authorizationToMatch) {
        settings.addOption(SETTING_AUTHORIZATION_TO_MATCH, authorizationToMatch);
    }

    public static void setFilters(IteratorSetting settings, EnumSet<ElementFilter> filters) {
        Set<String> filterStrings = new HashSet<>();
        for (ElementFilter filter : filters) {
            filterStrings.add(filter.name());
        }
        settings.addOption(SETTING_FILTERS, SetOfStringsEncoder.encodeToString(filterStrings));
    }

    @Override
    public void init(SortedKeyValueIterator<Key, Value> source, Map<String, String> options, IteratorEnvironment env) throws IOException {
        super.init(source, options, env);
        authorizationToMatch = options.get(SETTING_AUTHORIZATION_TO_MATCH);
        Set<String> filterStrings = SetOfStringsEncoder.decodeFromString(options.get(SETTING_FILTERS));
        List<ElementFilter> filtersCollection = new ArrayList<>();
        for (String filterString : filterStrings) {
            filtersCollection.add(ElementFilter.valueOf(filterString));
        }
        filters = EnumSet.copyOf(filtersCollection);
        matchCache = new HashMap<>();
    }

    @Override
    public SortedKeyValueIterator<Key, Value> deepCopy(IteratorEnvironment env) {
        HasAuthorizationFilter filter = (HasAuthorizationFilter) super.deepCopy(env);
        filter.authorizationToMatch = this.authorizationToMatch;
        filter.filters = this.filters;
        filter.matchCache = new HashMap<>();
        return filter;
    }

    @Override
    public boolean accept(Key k, Value v) {
        if (filters.contains(ElementFilter.ELEMENT)
            && (k.getColumnFamily().equals(EdgeIterator.CF_SIGNAL) || k.getColumnFamily().equals(VertexIterator.CF_SIGNAL))
            && isMatch(k)) {
            return true;
        }

        if (filters.contains(ElementFilter.PROPERTY) && k.getColumnFamily().equals(EdgeIterator.CF_PROPERTY) && isMatch(k)) {
            return true;
        }

        if (filters.contains(ElementFilter.PROPERTY_METADATA) && k.getColumnFamily().equals(EdgeIterator.CF_PROPERTY_METADATA) && isMatch(k)) {
            return true;
        }

        return false;
    }

    private boolean isMatch(Key k) {
        Text columnVisibilityText = k.getColumnVisibility();
        if (columnVisibilityText.getLength() == 0) {
            return false;
        }
        Boolean match = matchCache.get(columnVisibilityText);
        if (match != null) {
            return match;
        }

        String[] parts = SPLIT_PATTERN.split(k.getColumnVisibilityParsed().toString());
        for (String part : parts) {
            if (part.equals(authorizationToMatch)) {
                matchCache.put(columnVisibilityText, true);
                return true;
            }
        }
        matchCache.put(columnVisibilityText, false);
        return false;
    }
}
