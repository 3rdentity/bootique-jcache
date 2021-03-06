package io.bootique.jcache;

import io.bootique.annotation.BQConfig;
import io.bootique.annotation.BQConfigProperty;
import io.bootique.resource.ResourceFactory;
import io.bootique.shutdown.ShutdownManager;

import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@BQConfig("A collection of configuration files in the format understood by the provider.")
public class JCacheFactory {

    private List<ResourceFactory> configs;

    public JCacheFactory() {
        this.configs = Collections.emptyList();
    }

    @BQConfigProperty("A list of resource URLs pointing to cache configuration files in the format understood by the" +
            " underlying JCache provider.")
    public void setConfigs(List<ResourceFactory> configs) {
        this.configs = Objects.requireNonNull(configs);
    }

    public CacheManager createManager(Map<String, Configuration<?, ?>> configs, ShutdownManager shutdownManager) {

        CachingProvider provider;
        try {
            // TODO: an explicit config property to pick explicit provider out of available choices... though probably
            // pointless in most cases (do we realistically expect multiple providers on classpath?)
            provider = Caching.getCachingProvider();
        } catch (CacheException e) {
            throw new RuntimeException("'bootique-jcache' doesn't bundle any JCache providers. " +
                    "You must place a JCache 1.0 provider on classpath explicitly.", e);
        }

        shutdownManager.addShutdownHook(provider);

        CacheManager manager = getConfigUri().map(u -> provider.getCacheManager(u, null)).orElse(provider.getCacheManager());
        shutdownManager.addShutdownHook(manager);

        // now load contributed configs
        configs.forEach(manager::createCache);

        return manager;
    }

    private Optional<URI> getConfigUri() {

        switch (configs.size()) {
            case 0:
                return Optional.empty();
            case 1:
                try {
                    return Optional.of(configs.get(0).getUrl().toURI());
                } catch (URISyntaxException e) {
                    throw new IllegalStateException("Error converting config to URI: " + configs.get(0));
                }
            default:
                // TODO: how do we merge multiple configs?
                throw new IllegalStateException("More than one JCache configuration specified. Currently unsupported");
        }
    }
}
