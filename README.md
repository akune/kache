### An annotation processing-based method cache provider
#### Dependency after building locally: 
```
<project ...
    <dependencies>
        <dependency>
            <groupId>de.kune</groupId>
            <artifactId>kache-impl</artifactId>
            <version>0.1-SNAPSHOT</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>
</project>
```
#### Dependency using Jitpack
Check commits / revisions at https://jitpack.io/#akune/kache
```
<project ...
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
    <dependencies>
        <dependency>
            <groupId>com.github.akune.kache</groupId>
            <artifactId>kache-impl</artifactId>
            <version>ee787959af</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>
</project>
``` 
#### Usage:
```
@Cached
public long[] primeFactors(long input) {
    List<Long> result = new LinkedList<>();
    for(long i = 2; i< input; i++) {
        while(input%i == 0) {
            result.add(i);
            input = input/i;
        }
    }
    if(input >2) {
        result.add(input);
    }
    return result.stream().mapToLong(l -> l).toArray();
}
```
This will result in the following (decompiled) bytecode: 
```
private ConcurrentMap<Object, Object> _cache_primeFactors = new ConcurrentHashMap();

public long[] primeFactors(long input) {
    return (long[])((long[])this._cache_primeFactors.computeIfAbsent(Arrays.asList(input), (___cacheParam) -> {
        return this._doUncached_primeFactors(input);
    }));
}

private long[] _doUncached_primeFactors(long input) {
    List<Long> result = new LinkedList();

    for(long i = 2L; i < input; ++i) {
        while(input % i == 0L) {
            result.add(i);
            input /= i;
        }
    }

    if (input > 2L) {
        result.add(input);
    }

    return result.stream().mapToLong((l) -> {
        return l;
    }).toArray();
}
```
