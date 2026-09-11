package io.github.dmitriyiliyov.idempify.core.cache;

/**
 * Told what each cache lookup found, so a cache can be measured from outside without the measuring sitting
 * in the answer's way.
 * <p>
 * A hit means the cache answered the lookup. An entry that was found but had outlived the record it stands
 * for counts as a miss, because the lookup still had to go to the store; a distributed backend that lets its
 * own expiry evict the entry reports the same thing by never finding it.
 * <p>
 * An implementation is expected to return without throwing: it is called on the lookup path, and nothing
 * there shields a lookup from it today.
 */
public interface CacheEventListener {

    void onHit();

    void onMiss();

    CacheEventListener NOOP = new CacheEventListener() {
        @Override
        public void onHit() {}

        @Override
        public void onMiss() {}
    };
}
