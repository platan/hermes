package pl.allegro.tech.hermes.schema;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import pl.allegro.tech.hermes.api.Topic;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CachedCompiledSchemaRepository<T> implements CompiledSchemaRepository<T> {

    private final LoadingCache<TopicAndSchemaVersion, CompiledSchema<T>> cache;
    private final CompiledSchemaRepository<T> compiledSchemaRepository;

    public CachedCompiledSchemaRepository(CompiledSchemaRepository<T> delegate, long maximumCacheSize, int expireAfterAccessMinutes) {
        this.cache = CacheBuilder
                .newBuilder()
                .maximumSize(maximumCacheSize)
                .expireAfterAccess(expireAfterAccessMinutes, TimeUnit.MINUTES)
                .build(new CompiledSchemaLoader<>(delegate));
        this.compiledSchemaRepository = delegate;
    }

    @Override
    public CompiledSchema<T> getSchema(Topic topic, SchemaVersion version, boolean online) {
        try {
            if (online) {
                CompiledSchema<T> compiledSchema = compiledSchemaRepository.getSchema(topic, version);
                cache.put(new TopicAndSchemaVersion(topic, version), compiledSchema);
                return compiledSchema;
            }
            return cache.get(new TopicAndSchemaVersion(topic, version));
        } catch (Exception e) {
            throw new CouldNotLoadSchemaException(e);
        }
    }

    public void removeFromCache(Topic topic) {
        Set<TopicAndSchemaVersion> topicWithSchemas = cache.asMap().keySet().stream()
                .filter(topicWithSchema -> topicWithSchema.topic.equals(topic))
                .collect(Collectors.toSet());
        cache.invalidateAll(topicWithSchemas);
    }

    private static class CompiledSchemaLoader<T> extends CacheLoader<TopicAndSchemaVersion, CompiledSchema<T>> {

        private final CompiledSchemaRepository<T> delegate;

        public CompiledSchemaLoader(CompiledSchemaRepository<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompiledSchema<T> load(TopicAndSchemaVersion key) {
            return delegate.getSchema(key.topic, key.schemaVersion);
        }
    }

    private static class TopicAndSchemaVersion {

        private final Topic topic;
        private final SchemaVersion schemaVersion;

        TopicAndSchemaVersion(Topic topic, SchemaVersion schemaVersion) {
            this.topic = topic;
            this.schemaVersion = schemaVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TopicAndSchemaVersion that = (TopicAndSchemaVersion) o;
            return Objects.equals(schemaVersion, that.schemaVersion) &&
                    Objects.equals(topic, that.topic);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic, schemaVersion);
        }
    }

}
