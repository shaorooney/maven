package com.cantor.itd.secmaster.services;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.configuration.NearCacheConfiguration;

/**
 * @author eserebrinskiy
 * @since 2/16/2017
 */
@SuppressWarnings("unused")
public class SmServiceNearCacheImpl extends SmServiceImpl {
    @Override
    protected <K, V> IgniteCache<K, V> getCache(String cacheName) {
        return grid.getOrCreateNearCache(cacheName, new NearCacheConfiguration<>());
    }
}
