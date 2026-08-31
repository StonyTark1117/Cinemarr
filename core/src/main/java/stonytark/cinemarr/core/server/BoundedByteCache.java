package stonytark.cinemarr.core.server;

import java.util.LinkedHashMap;
import java.util.Map;

/** Access-ordered cache bounded by both entry count and retained bytes. */
public final class BoundedByteCache<K,V> {
    private final int maxEntries;private final long maxBytes;private final Sizer<V> sizer;
    private final LinkedHashMap<K,V> values=new LinkedHashMap<K,V>(16,0.75F,true);private long bytes;
    public BoundedByteCache(int maxEntries,long maxBytes,Sizer<V> sizer){if(maxEntries<1||maxBytes<1||sizer==null)throw new IllegalArgumentException("cache limits");this.maxEntries=maxEntries;this.maxBytes=maxBytes;this.sizer=sizer;}
    public synchronized V get(K key){return values.get(key);}
    public synchronized boolean put(K key,V value){if(key==null||value==null)throw new IllegalArgumentException("cache value");long size=size(value);V previous=values.remove(key);if(previous!=null)bytes-=size(previous);if(size>maxBytes)return false;values.put(key,value);bytes+=size;while(values.size()>maxEntries||bytes>maxBytes){Map.Entry<K,V> eldest=values.entrySet().iterator().next();values.remove(eldest.getKey());bytes-=size(eldest.getValue());}return values.containsKey(key);}
    public synchronized int size(){return values.size();}public synchronized long retainedBytes(){return bytes;}
    private long size(V value){long result=sizer.sizeBytes(value);if(result<0)throw new IllegalArgumentException("negative cache size");return result;}
    public interface Sizer<V>{long sizeBytes(V value);}
}
