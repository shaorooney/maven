package com.cantor.itd.secmaster.services;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cantor.itd.secmaster.cache.bpipe.CreateSecurityTask;
import com.cantor.itd.secmaster.cache.bpipe.UpdateEntryProcessor;
import com.cantor.itd.secmaster.cache.config.GridConfig;
import com.cantor.itd.secmaster.common.exceptions.SecMasterException;
import com.cantor.itd.secmaster.common.model.IdentifierType;
import com.cantor.itd.secmaster.common.model.Identifiers;
import com.cantor.itd.secmaster.common.model.metadata.Field;
import com.cantor.itd.secmaster.common.model.securities.Corp;
import com.cantor.itd.secmaster.common.model.securities.Equity;
import com.cantor.itd.secmaster.common.model.securities.Govt;
import com.cantor.itd.secmaster.common.model.securities.Mtge;
import com.cantor.itd.secmaster.common.model.securities.Muni;
import com.cantor.itd.secmaster.common.model.types.Tuple;
import com.cantor.itd.secmaster.common.services.ISmService;

import static com.cantor.itd.secmaster.common.Constants.CORP_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.EQUITY_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.FIELD_ID;
import static com.cantor.itd.secmaster.common.Constants.GOVT_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.GROUPS_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.IDENTIFIERS_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.MTGE_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.MUNI_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.SECTORS_CACHE_NAME;
import static com.cantor.itd.secmaster.common.Constants.SUB_GROUPS_CACHE_NAME;
import static com.cantor.itd.secmaster.common.model.metadata.FieldHelper.getNonBpipeFields;
import static com.cantor.itd.secmaster.common.model.metadata.FieldHelper.hasBpipeFields;
import static com.cantor.itd.secmaster.common.model.metadata.MetadataHelper.getMetadata;
import static com.cantor.itd.secmaster.common.util.LoggerUtils.withLogTime;
import static java.util.stream.Collectors.toList;

/**
 * @author eserebrinskiy
 * @since 12/9/2016
 */
public class SmServiceImpl implements ISmService {

    private static final Logger logger = LoggerFactory.getLogger(SmServiceImpl.class);
    protected final Ignite grid;
    private final IgniteCache<UUID, Identifiers> indCache;
    private final IgniteCache<UUID, Equity> equityCache;
    private final IgniteCache<UUID, Corp> corpCache;
    private final IgniteCache<UUID, Govt> govtCache;
    private final IgniteCache<UUID, Muni> muniCache;
    private final IgniteCache<UUID, Mtge> mtgeCache;
    private final IgniteCache<Integer, String> industrySectorCache;
    private final IgniteCache<Integer, String> industryGroupCache;
    private final IgniteCache<Integer, String> industrySubGroupCache;

    private boolean ownGrid = false;

    @SuppressWarnings("WeakerAccess")
    public SmServiceImpl() {
        this(Ignition.getOrStart(GridConfig.getGridConfig()));
        ownGrid = true;
    }

    public SmServiceImpl(Ignite grid) {
        this.grid = grid;
        indCache = getCache(IDENTIFIERS_CACHE_NAME);
        equityCache = getCache(EQUITY_CACHE_NAME);
        corpCache = getCache(CORP_CACHE_NAME);
        govtCache = getCache(GOVT_CACHE_NAME);
        muniCache = getCache(MUNI_CACHE_NAME);
        mtgeCache = getCache(MTGE_CACHE_NAME);
        industrySectorCache = getCache(SECTORS_CACHE_NAME);
        industryGroupCache = getCache(GROUPS_CACHE_NAME);
        industrySubGroupCache = getCache(SUB_GROUPS_CACHE_NAME);
    }

    @Override
    public final Collection<Map<String, Object>> query(String name, String token, IdentifierType identifierType, String identifier, String... fields) throws SecMasterException {
        return getIds(identifierType, identifier).stream().parallel()
                .map(id -> processSecurityRequest(id, name, token, fields))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    @Override
    public Map<String, Object> query(String name, String token, UUID uuid, String... fields) throws SecMasterException {
        return processSecurityRequest(uuid, name, token, fields);
    }

    @Override
    public final Collection<Map<String, Object>> queryEquity(String name, String token, IdentifierType identifierType, String identifier, String... fields) throws SecMasterException {
        return getIds(identifierType, identifier).stream().parallel()
                .map(id -> processSecurityRequest(equityCache, name, token, id, fields))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    @Override
    public final Map<String, Object> queryEquity(String name, String token, UUID uuid, String... fields) throws SecMasterException {
        return processSecurityRequest(equityCache, name, token, uuid, fields);
    }

    @Override
    public final Collection<Map<String, Object>> queryCorp(String name, String token, IdentifierType identifierType, String identifier, String... fields) throws SecMasterException {
        return getIds(identifierType, identifier).stream().parallel()
                .map(id -> processSecurityRequest(corpCache, name, token, id, fields))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    @Override
    public final Map<String, Object> queryCorp(String name, String token, UUID uuid, String... fields) throws SecMasterException {
        return processSecurityRequest(corpCache, name, token, uuid, fields);
    }

    @Override
    public final Collection<Map<String, Object>> queryGovt(String name, String token, IdentifierType identifierType, String identifier, String... fields) throws SecMasterException {
        return getIds(identifierType, identifier).stream().parallel()
                .map(id -> processSecurityRequest(govtCache, name, token, id, fields))
                .filter(Objects::nonNull)
                .collect(toList());
    }

    @Override
    public final Map<String, Object> queryGovt(String name, String token, UUID uuid, String... fields) throws SecMasterException {
        return processSecurityRequest(govtCache, name, token, uuid, fields);
    }

    @Override
    public final Collection<Map<String, Object>> queryMuni(String name, String token, IdentifierType identifierType, String identifier, String... fields) throws SecMasterException {
        Collection<Map<String, Object>> rv = getIds(identifierType, identifier).stream().parallel()
                .map(id -> processSecurityRequest(muniCache, name, token, id, fields))
                .filter(Objects::nonNull)
                .collect(toList());

        if (rv.size() == 0)
            return retrieveSecurity(name, token, identifierType, identifier, Muni.class, fields);
        return rv;
    }

    @Override
    public final Map<String, Object> queryMuni(String name, String token, UUID uuid, String... fields) throws SecMasterException {
        return processSecurityRequest(muniCache, name, token, uuid, fields);
    }

    @Override
    public Collection<Map<String, Object>> queryMtge(String name, String token, IdentifierType identifierType, String identifier, String... fields) throws SecMasterException {
        Collection<Map<String, Object>> rv = getIds(identifierType, identifier).stream().parallel()
                .map(id -> processSecurityRequest(mtgeCache, name, token, id, fields))
                .filter(Objects::nonNull)
                .collect(toList());

        if (rv.size() == 0)
            return retrieveSecurity(name, token, identifierType, identifier, Mtge.class, fields);
        return rv;
    }

    @Override
    public Map<String, Object> queryMtge(String name, String token, UUID uuid, String... fields) throws SecMasterException {
        return processSecurityRequest(mtgeCache, name, token, uuid, fields);
    }

    private Map<String, Object> processSecurityRequest(UUID id, String name, String token, String... fields) throws SecMasterException {
        String cacheName = indCache.get(id).cacheName();
        IgniteCache<UUID, ? extends Identifiers> cache = getCache(cacheName);
        return processSecurityRequest(cache, name, token, id, fields);
    }

    private Map<String, Object> processSecurityRequest(IgniteCache<UUID, ? extends Identifiers> cache, String name, String token, UUID uuid, String... fields) throws SecMasterException {
        BinaryObject bo = cache.<UUID, BinaryObject>withKeepBinary().get(uuid);
        if (bo == null) {
            logger.error("Security UUID={} is not found in cache={}", uuid, cache.getName());
            return null;
        }
        List<Field> bo_fields = getMetadata(bo, Identifiers::fieldsMetadata, fields).collect(toList());
        Map<String, Object> m;
        if (name != null && name.length() > 0) {
            Tuple<BinaryObject, Set<Field>> t = withLogTime("SmServiceImpl::validateBpipeFields", () -> validateBpipeFields(uuid, cache.getName(), bo_fields, token, name));
            m = withLogTime("SmService::createResponse", () -> createResponse(t, fields, bo_fields, t._1.field(FIELD_ID)));
        } else {
            if (hasBpipeFields(bo_fields))
                throw new SecMasterException("BPIPE service is not available. Please remove all the BPIPE fields from the request for UUID=" + uuid);
            else {
                m = new HashMap<>(bo_fields.size() + 1, 1);
                for (int i = 0; i < fields.length; ++i) {
                    Field f = bo_fields.get(i);
                    if (f != null) {
                        Object v = bo.field(f.name());
                        if (v != null)
                            m.put(fields[i], v);
                    }
                }
            }
        }
        return m;
    }

    private Map<String, Object> createResponse(Tuple<BinaryObject, Set<Field>> bpipeResponse, String[] requestFields, List<Field> fields, UUID id) {
        Map<String, Object> m = new HashMap<>(requestFields.length + 1, 1);
        Field f;
        for (int i = 0; i < requestFields.length; ++i)
            if ((f = fields.get(i)) != null && bpipeResponse._2.contains(f)) {
                Object val;
                if (f.isSchedule())
                    val = getCache(f.scheduleType().cacheName()).get(id);
                else
                    val = bpipeResponse._1.field(f.name());

                if (val instanceof BinaryObject)
                    val = ((BinaryObject) val).deserialize();
                if (val != null)
                    m.put(requestFields[i], val);
            }
        return m;
    }

    private Collection<UUID> getIds(IdentifierType identifierType, String identifier) {
        String mappingCache = identifierType.mappingCacheName;
        if (mappingCache == null)
            throw new SecMasterException("Unsupported search type");

        if (identifierType == IdentifierType.bbgUnique) {
            UUID id = this.<String, UUID>getCache(IdentifierType.bbgUnique.mappingCacheName).get(identifier);
            if (id == null)
                return Collections.emptyList();
            return Collections.singletonList(id);
        } else {
            Collection<UUID> rv = this.<String, Set<UUID>>getCache(mappingCache).get(identifier);
            if (rv == null)
                return Collections.emptyList();
            return rv;
        }
    }

    @Override
    public final Map<Integer, String> getAllIndustrySectors() {
        Map<Integer, String> m = new HashMap<>();
        industrySectorCache.forEach(e -> m.put(e.getKey(), e.getValue()));
        return m;
    }

    @Override
    public final Map<Integer, String> getAllIndustryGroups() {
        Map<Integer, String> m = new HashMap<>();
        industryGroupCache.forEach(e -> m.put(e.getKey(), e.getValue()));
        return m;
    }

    @Override
    public final Map<Integer, String> getAllIndustrySubGroups() {
        Map<Integer, String> m = new HashMap<>();
        industrySubGroupCache.forEach(e -> m.put(e.getKey(), e.getValue()));
        return m;
    }

    @Override
    public void start() throws SecMasterException {
        // No-op
    }

    @Override
    public void stop() throws SecMasterException {
        ISmService.super.stop();
        if (ownGrid) {
            grid.close();
        }
    }

    private Collection<Map<String, Object>> retrieveSecurity(String name, String token, IdentifierType identifierType, String identifier, Class<? extends Identifiers> clazz, String... fields) {
        // No security were found
        // Trying to create it
        List<Field> bo_fields = getMetadata(clazz, Identifiers::fieldsMetadata, fields).collect(toList());


        Collection<Tuple<BinaryObject, Set<Field>>> resp = withLogTime("grid::CreateSecurityTask", () -> grid.compute().call(new CreateSecurityTask(clazz, bo_fields, name, identifierType, identifier, token)));
        return resp.stream()
                .map(t -> createResponse(t, fields, bo_fields, t._1.field(FIELD_ID)))
                .collect(toList());
    }

    protected <K, V> IgniteCache<K, V> getCache(String cacheName) {
        return grid.cache(cacheName);
    }

    private Tuple<BinaryObject, Set<Field>> validateBpipeFields(UUID id, String cacheName, Collection<Field> fields, String userToken, String userName) {
        Objects.requireNonNull(fields);
        Objects.requireNonNull(id);
        Objects.requireNonNull(cacheName);

        IgniteCache<UUID, BinaryObject> cache = grid.cache(cacheName).withKeepBinary();

        if (userName == null || userName.isEmpty())
            return new Tuple<>(cache.get(id), getNonBpipeFields(fields));

        // Check if we have any bpipe fields in the list
        if (!hasBpipeFields(fields))
            return new Tuple<>(cache.get(id), new HashSet<>(fields));

        Object rv = cache.invoke(id, new UpdateEntryProcessor(), fields, userName, userToken);
        if (rv instanceof BinaryObject)
            return ((BinaryObject) rv).deserialize();
        //noinspection unchecked
        return (Tuple<BinaryObject, Set<Field>>) rv;
    }
}
